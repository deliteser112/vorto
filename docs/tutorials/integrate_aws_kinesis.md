# Integrating AWS Kinesis with Eclipse Vorto for IoT anomaly detection

In this tutorial, you are going to learn, how you can use [Eclipse Vorto](https://www.eclipse.org/vorto) and [AWS Kinesis](https://aws.amazon.com/kinesis/) in order to analyze device data in realtime. 

<img src="../images/tutorials/integrate_kinesis/cover.png"/>

> **What is AWS Kinesis?** 

> With AWS Kinesis, you can collect and process large amount of data in real time, for example to do anomaly detection or other cool stuff.

We are going to use the GPS sensors, that we had described and connected with Vorto in the [mapping pipeline tutorial](create_mapping_pipeline.md). Make sure you had worked through that tutorial thoroughly before proceeding because it gives you a very good understanding of the essence of the Eclipse Vorto project and prepares everything you need for this tutorial. 
Ready? Great. Let's proceed. 

As shown in the following illustration, we send GPS location data from two different sensors using different data formats via MQTT to Eclipse Hono. The [Eclipse Vorto normalization middleware](https://github.com/eclipse/vorto-examples/blob/master/vorto-middleware/Readme.md) consumes the sensor data and uses the [Vorto AWS Kinesis plugin](https://github.com/eclipse/vorto-examples/blob/master/vorto-middleware/middleware-ext-kinesis/Readme.md), in order to transform the data to a normalized, semantically enriched JSON before forwarding this data to an AWS Kinesis data stream. In Kinesis we will then write a small application, that analyzes the harmonized location data.
 
<img src="../images/tutorials/integrate_kinesis/overview_kinesis_vorto.png"/>


## Prerequisites

* Successfully completed the [mapping pipeline tutorial](create_mapping_pipeline.md)

<br />

## Steps

Here are the steps, that we are going to take during this tutorial:

1. Setting up Kinesis on AWS
2. Configuring and starting the Vorto middleware with your AWS Kinesis data stream settings
3. Sending device data to Eclipse Hono via MQTT and monitor the incoming data in the Eclipse Vorto middleware dashboard
4. Creating an AWS Kinesis analytics application
5. Testing the analytics application

<br />

## Step 1: Setting up AWS Kinesis

1. Log on to AWS Management Console 
2. Open the AWS Kinesis Dashboard
3. Click **Create Data Stream**
4. Specify a stream name, e.g. *vortoDemo*
5. Specify number of shards. Let's start with **1** for the time being. This number highly depends on the amount of data you are going to process in Kinesis.
6. Confirm with **Create Kinesis Stream** 

## Step 2: Configurating & Starting the Eclipse Vorto Middleware

1. Head over to AWS IAM, and create a technical user with AWS Kinesis full access permissions. Keep note of the access key and secret key. 
2. Start the Eclipse Vorto Middleware docker with the required [Kinesis environment variables](https://github.com/eclipse/vorto-examples/blob/master/vorto-middleware/middleware-ext-kinesis/Readme.md#configuration) ```docker run -it -p 8080:8080 -v //C/absolute_local_dir:/mappings -p 8080:8080 -e mapping_spec_dir=/mappings -e github.client.clientId=your_github_clientid -e github.client.clientSecret=your_github_clientsecret -e hono.tenantId=your_tenantId -e hono.password=your_hono_password -e kinesis.streamName=vortoDemo -kinesis.accessKey=mykey -e kinesis.secretKey=mysecret eclipsevorto/vorto-normalizer:nightly```
3. Once the service has started successfully, open the local [Eclipse Vorto middleware dashboard](http://localhost:8080/#/plugins). You should see the AWS Kinesis plugin in active mode (green light). 

**Congrats!** Your middleware is all set now to receive IoT device data from Eclipse Hono protocol adapters and forward it to your AWS Kinesis stream.

## Step 3: Sending device data

Send some location data to Eclipse Hono via MQTT 

```mosquitto pub ....```

Open the [Vorto Middleware Monitoring dashboard](http://localhost:8080/#/monitoring) and observe the logs. You should see something like this: 

//TODO: Fixme to show the monitoring logs showing the gps sensor data
<img src="../images/tutorials/integrate_kinesis/kinesis_logs.png"/>

## Step 4: Creating an AWS Kinesis analytics application

1. Open the Kinesis service dashboard in AWS
2. Click **Create Analytics Application**
3. Specify a name, e.g. *VortoDemoDataAnalytics*
4. Select **SQL** for runtime
5. Confirm with **Create Application**
6. Select **Connect streaming data** and select **Kinesis data stream** as source
7. Choose the data stream, we had created in step 1, e.g. *vortoDemo*
8. Leave the other selections as default.
9. Click **Discover schema**. Make sure you are sending data to the Vorto middleware, so that AWS is able to discover the schema from the data it receives. You should be able to see a table, similar to this:<img src="../images/tutorials/integrate_kinesis/kinesis_discover_schema.png"/>
10. Select **Save and confinue**
11. Choose **Go to SQL Editor** in order to process the incoming device data. Copy the following SQL snippet in the SQL editor and **save&run** the SQL: 

```
HERE goes the SQL analytics snippet for the gps analytics 
```

TODO: Explain the SQL snippet.

## Step 5: Testing the analytics application

In this step, we are going to send some test data of our first GPS sensor with location data, **not meeting the geofence condition**.  

```mosquitto pub sending JSON with location data```

Now, we are sending test data of the second gps sensor in CSV format that **meets the geofence condition**:

```mosquitto pub sending CSV with location data```

Now you can check our Kinesis analytics application where the destination stream now contains the data of our second sensor:

ADD ILLUSTRATION HERE

## What's next?

* Jump over to the [AWS Kinesis documentation](https://docs.aws.amazon.com/kinesis/index.html) to find out more about how further process your IoT device data.


---

In case you're having difficulties or facing any issues, feel free to [create a new question on StackOverflow](https://stackoverflow.com/questions/ask) and we'll answer it as soon as possible!   
Please make sure to use `eclipse-vorto` as one of the tags. 
