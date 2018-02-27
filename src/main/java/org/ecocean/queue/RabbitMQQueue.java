package org.ecocean.queue;

import java.util.Properties;
import java.util.function.Function;
import org.ecocean.ShepherdProperties;
import org.ecocean.servlet.ServletUtilities;
import javax.servlet.http.HttpServletRequest;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

/*

A light wrapper to RabbitMQ libs.  possibly(?) allow for exansion later to support an underlying base abstract class
(e.g. Queue.java)

*/

public class RabbitMQQueue extends Queue {
    private static ConnectionFactory factory = null;
    private static Connection connection = null;
    private static String EXCHANGE_NAME = "";  //default... i think this is kosher?

    private Channel channel = null;
    //private String queueName = null;

/*
    private static Logger logger = LoggerFactory.getLogger(AssetStore.class);

    private static Map<Integer, AssetStore> stores;

    protected Integer id;
    protected String name;
*/

    public RabbitMQQueue(final String name) throws java.io.IOException {
        super(name);
        if (factory == null) throw new java.io.IOException("RabbitMQQueue.init() has not yet been called!");
        //queueName = name;
        try {
            channel = getChannel();
            //channel.exchangeDeclare(EXCHANGE_NAME, "direct", true); //lets use "default" exchange?
            channel.queueDeclare(name, true, false, false, null);
            //channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
        } catch (java.util.concurrent.TimeoutException toex) {
            throw new java.io.IOException("RabbitMQQueue(" + name + ") TimeoutException: " + toex.toString());
        }
    }

    public static synchronized void init(HttpServletRequest request) throws java.io.IOException {
        String context = ServletUtilities.getContext(request);
        if (factory != null) return;
        Properties props = ShepherdProperties.getProperties("queue.properties", "", context);
        if (props == null) throw new java.io.IOException("no queue.properties");
        try {
            factory = new ConnectionFactory();
            factory.setUsername(props.getProperty("rabbitmq_username", "guest"));
            factory.setPassword(props.getProperty("rabbitmq_password", "guest"));
            factory.setVirtualHost(props.getProperty("rabbitmq_virtualhost", "/"));
            factory.setHost(props.getProperty("rabbitmq_host", "localhost"));
            factory.setPort(Integer.parseInt(props.getProperty("rabbitmq_port", "5672")));
            checkConnection();
        } catch (java.util.concurrent.TimeoutException toex) {
            throw new java.io.IOException("RabbitMQ.init() TimeoutException: " + toex.toString());
        }
System.out.println("[INFO] RabbitMQQueue.init() complete");
    }

    public static void checkConnection() throws java.io.IOException, java.util.concurrent.TimeoutException {
        if ((connection != null) && connection.isOpen()) return;
        connection = factory.newConnection();
    }

    public Channel getChannel() throws java.io.IOException, java.util.concurrent.TimeoutException {
        checkConnection();
        return connection.createChannel();
    }

    
    public void publish(String msg) throws java.io.IOException {
//TODO check connection *and* channel??
        //channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, null, msg.getBytes());
        channel.basicPublish(EXCHANGE_NAME, this.queueName, null, msg.getBytes());
System.out.println("[INFO] published to {" + this.queueName + "}: " + msg);
    }

    //i think this never returns?
    public void consume(final QueueMessageHandler msgHandler) throws java.io.IOException {
        //boolean is auto-ack.  false means we manually ack
        channel.basicConsume(this.queueName, false, "myConsumerTag",
            new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] bodyB) throws java.io.IOException {
                    String body = new String(bodyB);
                    String routingKey = envelope.getRoutingKey();
                    String contentType = properties.getContentType();
                    long deliveryTag = envelope.getDeliveryTag();
                    // (process the message components here ...)
                    boolean success = msgHandler.handler(body);
System.out.println("RabbitMQQueue.consume(): " + deliveryTag + "; " + contentType + "; " + routingKey + " = {" + body + "} => " + success);
                    if (success) channel.basicAck(deliveryTag, false);
                }
            }
        );
    }


}
