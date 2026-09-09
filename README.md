# Stock Trading Microservices (CS 677 Lab 3)

A small stock trading service built as four Java HTTP microservices that talk over REST, packaged with Docker Compose and deployed to a single AWS EC2 instance. Clients look up stock prices and place buy/sell orders, and the order service is replicated three ways so trading keeps working when a replica goes down.

## Architecture

A client hits the frontend on port 8080, which serves stock lookups out of an in-memory LRU cache (falling back to the catalog service on 8081) and forwards trades to whichever of the three order replicas (8082, 8083, 8084) is currently leader.

| Service | Port | What it holds |
|---|---|---|
| frontend | 8080 | LRU cache of stock lookups, current leader address |
| catalog | 8081 | Stock name, price, volume, quantity. Backed by `stocks.csv` |
| orderservice0/1/2 | 8082 / 8083 / 8084 | Order log keyed by transaction number. Backed by `orders.csv` |

A few things worth knowing about how the pieces interact:

- **Cache invalidation is pushed, not polled.** When the catalog approves a trade it calls `GET /updateCache?name=<stock>` on the frontend, which drops that entry. So the cache never serves a price that a completed trade has already changed.
- **Locking is per stock, not global.** The catalog keeps a `ReentrantLock` per stock for trades and a `ReentrantReadWriteLock` per stock for lookups, so trades on different stocks don't block each other. The cache uses the same idea for invalidation.
- **State is written back on a timer.** Both the catalog and the order replicas flush their in-memory maps to CSV every 30 seconds, and reload from CSV on startup.

## Why leader-based replication

The order service needs a single writer. Transaction numbers are assigned sequentially and every replica has to agree on which number maps to which order, so letting any replica accept a trade would mean either coordinating number assignment or resolving conflicts after the fact. Routing every trade through one leader makes that problem go away, and the interesting failure handling then lives in two small places instead of being spread through the system.

How it actually works:

- **Election is done by the frontend, not by the replicas.** `ElectOrderServiceLeader()` pings `/health` on replicas 2, 1, 0 in that order and takes the first one that answers, so the highest-numbered live replica wins. It runs at startup and again any time a request to the leader throws.
- **Replicas figure out their own role from traffic.** A replica that gets an order on `/trade` sets `isLeader = true`. One that gets an order on `/update` sets it to false. There is no separate leader handshake.
- **Propagation happens before the client hears back.** The leader writes its own log, POSTs the order to both followers, then replies with the transaction number.
- **Recovery is pull-based.** A restarting replica calls `GET /inform?transactionNumber=<last>` on the others and replays whatever it missed. A `-1` means it has nothing and wants the whole log.

The trade-off is that propagation is best effort. If a follower is unreachable the leader logs the failure and moves on, so followers can drift until they restart and catch up through `/inform`. That was an acceptable trade for this lab, but it is not a consensus protocol and it does not survive a leader that dies mid-write.

Connection timeouts throughout the app are deliberately short (20 to 40 ms). Everything runs on one host, so anything slower than that almost certainly means the other end is dead, and failing fast is what keeps a crashed leader from being visible to the client.

## What we measured and what we found

We ran 5 clients against the EC2 deployment, 1000 requests each, sweeping the follow-up trade probability `p` from 0 to 0.8 in steps of 0.2, with caching on and then off. Average latency in milliseconds:

| p | lookup, cached | lookup, no cache | trade, cached | trade, no cache |
|---|---|---|---|---|
| 0.0 | 77 | 97 | - | - |
| 0.2 | 80 | 100 | 87 | 105 |
| 0.4 | 84 | 103 | 85 | 109 |
| 0.6 | 93 | 96 | 93 | 96 |
| 0.8 | 101 | 90 | 100 | 97 |

**Caching helps below p = 0.6 and stops helping above it.** At low trade probability it takes roughly 20 ms off both lookups and trades, about a 20% improvement. At p = 0.6 the two configurations are within a couple of milliseconds of each other. At p = 0.8 caching is slightly *worse*: 101 ms vs 90 ms on lookups.

The crossover comes from invalidation. Every successful trade takes the write lock on that stock's cache entry and removes it. As `p` climbs there are more trades and fewer lookups, so entries get invalidated faster than they get reused, and lookups end up queuing behind invalidation locks for a cache that rarely hits. Trades pay for it twice, since they already contend for locks in the catalog and now wait on the cache lock as well. Below p = 0.6 the hit rate is high enough that the saved round trips to the catalog more than cover the lock contention.

The takeaway is that the cache is only worth having on a read-heavy workload, which is what we would expect but is easy to assume rather than check.

## Running it locally

Everything runs in containers, so Docker and Docker Compose are the only prerequisites. From the repo root:

```bash
./build.sh
```

That is a wrapper around `docker-compose -f 'docker-compose-local.yml' up -d --build`, which starts the catalog, all three order replicas, the frontend, and one client.

Check that it came up:

```bash
curl http://localhost:8080/stocks/GameStart
```

To run clients separately from the services, bring up the services with `docker-compose-local.yml` and use `docker-compose-client.yml` for the clients. That file defines two services: `client` for a single client, and `clientforloadtesting` which runs 5 replicas for load testing.

```bash
docker-compose -f 'docker-compose-client.yml' up -d --build 'clientforloadtesting'
```

To run with caching off, point the frontend at `Dockerfile.frontendCachingDisabled` instead, or use `docker-compose-AWS-caching-disabled.yml`.

# Steps for deployment on AWS EC2

## Launch an EC2 Instance
Go to the AWS EC2 instance launch page:
```
https://us-east-1.console.aws.amazon.com/ec2/home?region=us-east-1#LaunchInstances:
```

### Name
Enter `CS677-lab3`

### Application and OS Images 
Select `Amazon Linux` under Quick Start

Amazon Machine Image (AMI): Select `Amazon Linux 2 AMI (HVM) - Kernel 5.10, SSD Volume Type`

Architecture: Select `64-bit (x86)`

### Instance Type
Select `t2.medium`

### Key Pair (Login)
Click `Create a new key pair`

Name `lab3-ec2-auth`, Type `RSA`, Format `.pem`. Click `Create key pair`

This will download the file `lab3-ec2-auth.pem`, move it to the project root directory

You can verify your inputs on the website with the below screenshots.

![EC2-connect](/images/ec2-instance-initials.png)
![EC2-connect](/images/Create2InstType.png)
![EC2-connect](/images/CreateKeyPair.png)

### Network Settings
Firewall (Security Groups): Select `Create security group`. This will create a new security group called "launch-wizard-<some_number>"

Check all three boxes (SSH, HTTPS, HTTP). Allow SSH Trafic From: Select `Anywhere 0/0.0.0.0`

![EC2-connect-network](/images/network-settings.png)

### Configure Storage, Advanced Details
Keep default values. Click `Launch instance`.

![EC2-connect-advanced](/images/ec2-instance-configure-storage.png)

## Connect to EC2 Instance
Go to your instance, either by clicking on its ID or going to EC2 > Instances, and selecting your instance. Click the `Connect` button in the top right.

![EC2-connect](/images/ec2-connect.png)
![EC2-connect-2](/images/ec2-connect-2.png)
![EC2-connect-2](/images/InstanceTerminalPublicIP.png)


## Execute Commands in EC2 Instance Terminal
### Install Docker
```
sudo yum update -y
sudo amazon-linux-extras install docker
```
Enter "y" when prompted.

Allow user to run docker commands:
```
sudo usermod -a -G docker ec2-user
groups
```
If `groups` output does not conain "docker", close the page and reconnect. This will ensure docker has the correct permissions. If you needed to do this, run `groups` again and confirm "docker" is present in the ouput.

### Download docker-compose
```
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
```
Make execuatable, start the docker service, and create a directory named lab3
```
sudo chmod +x /usr/local/bin/docker-compose
mkdir lab3
cd lab3
sudo service docker start
```

## Execute Commands in Local Terminal
Ensure you are in the root directory of our repository

### Make PEM Read Only
```
chmod 600 lab3-ec2-auth.pem
```

### Copy Top-Level Files from Project Folder to EC2
Change the IP in the following command (0.0.0.0) to the instance's public IPv4 address. Enter "yes" when propmted.
```
scp -i lab3-ec2-auth.pem docker-compose-AWS.yml docker-compose-AWS-caching-disabled.yml Dockerfile.catalog Dockerfile.frontend Dockerfile.frontendCachingDisabled Dockerfile.order ec2-user@0.0.0.0:/home/ec2-user/lab3
```

### Recursively Copy Application Code Files to EC2
Change the IP in the following command (0.0.0.0) to the instance's public IPv4 address
```
scp -i lab3-ec2-auth.pem -r src/catalog src/frontend src/order/ ec2-user@0.0.0.0:/home/ec2-user/lab3
```

## Execute Commands in EC2 Instance Terminal

### Verify and Restructure Files
`ls` - Verify that you are in the "lab3" directory and that the copied files are present
```
mkdir src
mv catalog frontend order src/
```

### Build and Run Application on AWS with Caching Enabled
```
docker-compose -f 'docker-compose-AWS.yml' up -d --build
```

## On AWS Webiste
Go to EC2 Instance, Security Tab, Click on Security Group ("launch-wizard-<some_number>"). Click "edit-inbound rules". Don't remove rules that are already in place.

For each port (8080-8084), add a new rule:

`Custom TCP`, <port_number>, `Anywhere IPv4`, `0.0.0.0/0`

Click "save rules"

![security-group](/images/security-group.png)
![security-group-button](/images/EditInboundRulesButton.png)
![port-security-rules](/images/port-security-rules.png)

## Run Client
Remember "your IP" is your instance's public IPv4.
First, we can test if the app is working by opening a browser and entering: (again replace 0.0.0.0 with your IP)
```
http://0.0.0.0:8080/stocks/GameStart
```

If successful, on your local machine open the `Dockerfile.client` in the project folder and add your IP to the last line.

Change:
```
ENTRYPOINT ["java", "-jar", "client-service.jar", "0.6", "100", "true"]
```
To:
```
ENTRYPOINT ["java", "-jar", "client-service.jar", "0.6", "100", "true", "<your IP>"]
```
There are additional arguments you can pass and modify the `Dockerfile.client`. Detailed information about these arguments is provided in the design document.

In your local terminal run:
```
docker-compose -f 'docker-compose-client.yml' up -d --build 'client'
```

You should see the clients communicating with the server in the docker terminal / docker desktop.

## Test with Caching Disabled
In AWS terminal, stop all containers:
```
docker stop $(docker ps -q)
```
Build and run containers with caching disabled:

```
docker-compose -f 'docker-compose-AWS-caching-disabled.yml' up -d --build
```

In local terminal, start client:

```
docker-compose -f 'docker-compose-client.yml' up -d --build 'client'
```

Observe average latency difference.

# Reference

## API

What clients call on the frontend:

| Request | Description | Response |
|---|---|---|
| `GET /stocks/<stock_name>` | Look up a stock | `{"data": {name, price, quantity}}` or `{"error": ...}` |
| `POST /orders/` | Place a trade, body `{name, quantity, type}` where type is `buy` or `sell` | `{"data": {transaction_number}}` or `{"error": ...}` |
| `GET /orders/<order_number>` | Look up a past order | `{"data": {name, quantity, type}}` or `{"error": ...}` |

Internal endpoints, not meant for clients:

| Endpoint | Called by | Purpose |
|---|---|---|
| `GET /updateCache?name=<stock>` on frontend | catalog | Invalidate one cache entry after a trade |
| `GET /catalog?stock=<name>` on catalog | frontend | Stock lookup |
| `POST /catalog` on catalog | order leader | Ask whether a trade can go through |
| `GET /health` on order | frontend | Liveness check during election |
| `POST /trade` on order | frontend | Place a trade. Receiving this marks the replica leader |
| `POST /update` on order | order leader | Replicate an order. Receiving this marks the replica follower |
| `GET /inform?transactionNumber=<n>` on order | recovering replica | Ask the leader for orders missed since `n` |

## Configuration

**Frontend** takes its arguments in `Dockerfile.frontend`:

- `args[0]` caching on/off, default `true`
- `args[1]` cache capacity, default `5`

`Dockerfile.frontendCachingDisabled` is the same image with `false` passed in.

**Client** takes its arguments in `Dockerfile.client`:

- `args[0]` probability that a lookup is followed by a trade, default `0.8`
- `args[1]` total requests, default `8`
- `args[2]` print per-request and average latency, default `false`
- `args[3]` frontend IPv4, needed only when the services are on EC2 and the client is local

After finishing its requests, the client replays every successful trade through `GET /orders/<order_number>` and compares the response against what it recorded locally. Mismatches get printed. This is how we checked that replication was not losing or reordering anything.

**Order replicas** identify themselves through environment variables. Each replica gets `orderservice0/1/2`, and its own entry is set to the literal string `self`. That is how a replica learns its own id without hardcoding anything per container, which keeps all three genuinely identical builds.

## Tests

JUnit tests live in `test/` and run against a live deployment, so bring the services up first.

- `CatalogServiceTest` (2 tests) hits the catalog directly on 8081
- `OrderServiceTest` (7 tests) hits an order replica directly on 8082
- `FullApplicationTest` (9 tests) goes through the frontend on 8080
- `CommonMethods` is a shared HTTP helper, not a test class

Note that `Tesla` is used as the known-missing stock in the not-found tests. The catalog seeds ten stocks and Tesla is deliberately not one of them.

## Repo layout

```
src/catalog     catalog service
src/order       order service, built three times as the replicas
src/frontend    frontend service and LRU cache
src/client      load-generating client
test/           JUnit tests
data/           CSV state, mounted into the containers (gitignored)
docs/           design doc, evaluation doc, test output
images/         screenshots for the AWS walkthrough above
```

Compose files:

- `docker-compose-local.yml` everything plus one client, for local work
- `docker-compose-AWS.yml` services only, caching on
- `docker-compose-AWS-caching-disabled.yml` services only, caching off
- `docker-compose-client.yml` clients only, either one or five

## Docs

- `docs/Design Document.pdf` full design writeup for caching, replication, and fault tolerance
- `docs/Evaluation Document.pdf` latency measurements and the charts the table above is drawn from
- `docs/Tests Output.pdf` test run output plus the cache replacement and crash simulation answers

## Known limitations

- Follower updates are fire and forget. A follower that is down when a trade lands misses it until it restarts and pulls from `/inform`.
- Transaction numbers come from the size of the order log, so a gap or an out-of-order replay would shift numbering. Recovery replays in order, which is what keeps this correct in practice.
- Election is per frontend. There is only one frontend here, so replicas never see conflicting opinions about who leads, but nothing in the protocol would prevent that with more than one.
- Catalog state is a single instance. It is not replicated, so it is the remaining single point of failure.
