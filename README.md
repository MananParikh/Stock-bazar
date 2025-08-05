# Group Work Division
We worked together as a team for this lab, ensuring equal contribution from both members. We held daily meetings on Google Meet to discuss progress, address challenges, and plan the next steps. This regular communication allowed us to divide tasks efficiently and maintain a consistent pace throughout the project.

## Late days used
Late days used on this lab: 0

Late days used so far: Noah - 3, Manan - 2

## Manan Parikh (mananrajeshb@umass.edu)
- Implemented Caching
- Developed new API end-point: /orders/<order_number>
- Updated client file, to send order-lookup requests
- Worked on figuring out AWS deployment together
- Wrote test cases
- Wrote evaluation doc, output file, and design doc
- Analyzed results and created graphs for evaluation document.

## Noah Walls (nwalls@umass.edu)
- Implemented Order Service replication
- Restructured dockerfiles to support multiple order replicas
- Designed leader crash handling and re-election mechanism in Frontend
- Implemented Fault-Tolerance
- Worked on figuring out AWS deployment together
- Wrote and edited design doc

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
