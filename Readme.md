# Actor - based Middleware for the IoT

## 🧭 Introduction
This project is an **actor-based middleware** for **IoT applications**, built using the **Akka** framework. <br>
It leverages Akka's powerful concurrency model to manage communication between distributed IoT devices efficiently and reliably.<br>
The system uses **MQTT Mosquitto** broker for lightweight messaging and a **Dockerized PostgreSQL** for persistent storage. <br>
It's designed to be scalable, resilient, and well-suited for real-time data processing in IoT environments adhering to the reactive manifesto.

# Scala Akka IoT Middleware

This project is an **actor-based middleware** for **IoT applications**, built using the **Akka** framework. It leverages Akka's actor model to manage and coordinate communication between distributed IoT devices in a scalable and resilient way. The system uses **MQTT** (via Mosquitto) for lightweight messaging and a **Dockerized database** for persistent storage.


## 🚀 Features

- Actor-based concurrency using Akka
- MQTT integration with Eclipse Mosquitto
- Dockerized database and broker setup
- Configurable via HOCON (`application.conf`)
- Easily extensible for edge and cloud-based IoT systems


## 📦 Prerequisites

Before you begin, make sure you have installed the following tools:

- **[Scala](https://www.scala-lang.org/)** - the language the actor - based middleware is build with
- **[SBT](https://www.scala-sbt.org/)** – for building and running the Scala app
- **[Docker](https://www.docker.com/)** – for containerized services
- **[Docker Compose](https://docs.docker.com/compose/)** – to orchestrate containers

## 🐳 Up and Running 
Follow these steps to get the application and its services running locally:

### 1. Start Docker Services

First, make sure Docker is running on your machine. Then, use the following command to start the required services (database and MQTT broker):

```bash
docker-compose up -d
```

You can check the status of the services with:
```bash
docker-compose ps
```
### 2. Run the Scala/Akka Application

With the services running, start the Akka application using SBT:
```bash
sbt run
```
This will:
* Compile the Scala project
* Launch the Akka actor system
* Connect to the MQTT broker and the database
* Begin processing messages from IoT devices

Alternatively, you can also use compiler flags for the Scala compiler and the JVM:
```bash
sbt -J-XX:+UseParallelGC -J-XX:MaxGCPauseMillis=200 -J-XX:NewRatio=2 -J-XX:SurvivorRatio=8 -J-XX:MaxTenuringThreshold=1 -J-XX:InlineSmallCode=2000 -J-Xss256k run
```

## 📡 Use the gRPC API
This application supports **gRPC** using **Protocol Buffers** for efficient, 
strongly typed and language agnostic communication between IoT devices and the middleware.
The detailed definitions of the protocol buffers can be found under **src.main.proto**.

### 1. Device Provisioning API

```bash
DeviceProvisioningService/AddBrokerConfig(MQTT)
DeviceProvisioningService/RemoveBrokerConfig(ID)
DeviceProvisioningService/GetBrokerConfig(ID)
```

### 2. Device State API
```bash
StateService/UpdateState(StateUpdate)
StateService/GetState(DeviceID)
```
### 3. Telemetry API
```bash
DeviceRecords/GetLatestRecord(Device)
```

### 4. Command API
```bash
CommandService/SendCommandToDevice(CommandRequest)
```