package com.myorg;

import lombok.val;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;
import software.amazon.awscdk.services.ecs.AddCapacityOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterProps;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedEc2Service;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedEc2ServiceProps;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.DatabaseInstanceProps;
import software.constructs.Construct;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.Queue;

import java.util.HashMap;

public class StudentErpRefactorStack extends Stack {
    public StudentErpRefactorStack(final Construct parent, final String id) {
        this(parent, id, null);
    }

    public StudentErpRefactorStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

//        References:
//        https://www.luminis.eu/blog/how-to-use-java-cdk-to-define-a-dynamodb-backed-rest-api-with-only-aws-api-gateway-part-2/
//        https://github.com/spring-projects/spring-petclinic/tree/e280d12144b388ea58f90961f9b22500b29c8e3e

        val vpc = createVpc();
        val dockerImageAsset = createDockerImageAsset();

        val cluster = createCluster(vpc);

        val rds = createRds(vpc);
        val loadBalancer = createLoadBalancedEc2Service(cluster, dockerImageAsset, rds);

    }

    private ApplicationLoadBalancedEc2Service createLoadBalancedEc2Service(Cluster cluster, DockerImageAsset dockerImageAsset,
                                                                           DatabaseInstance rds) {

        return new ApplicationLoadBalancedEc2Service(this, "Ec2Service",
                ApplicationLoadBalancedEc2ServiceProps.builder()
                        .cluster(cluster)
                        .memoryLimitMiB(1024)
                        .serviceName("spring-petclinic")
                        .desiredCount(2)
                        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(ContainerImage.fromDockerImageAsset(dockerImageAsset))
                                .containerName("spring-petclinic")
                                .containerPort(8000)
                                .environment(new HashMap<String, String>() {{
                                    put("SPRING_DATASOURCE_PASSWORD","Welcome#12345");
                                    put("SPRING_DATASOURCE_USERNAME","");
                                    put("SPRING_PROFILES_ACTIVE","");
                                    put("SPRING_DATASOURCE_URL","jdbc:mysql://" + rds.getDbInstanceEndpointAddress() + "/petclinic?useUnicode=true&enabledTLSProtocols=TLSv1.2");
                                }})
                                .build())
                        .build());
    }

    private DockerImageAsset createDockerImageAsset() {
        return DockerImageAsset.Builder.create(this, "spring-petclinic")
                .directory("./docker/")
                .buildArgs(
                        new HashMap<String, String>() {{
                            put("JAR_FILE", "spring-petclinic-2.6.0-SNAPSHOT.jar");
                        }}
                )
                .build();
    }

    private Cluster createCluster(Vpc vpc) {
        val cluster = new Cluster(this, "Ec2Cluster", ClusterProps.builder().vpc(vpc).build());
        cluster.addCapacity("DefaultAutoScalingGroup", AddCapacityOptions.builder()
                .instanceType(new InstanceType("t2.micro"))
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .build());

        return cluster;
    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "CdkVpc")
                .cidr("10.0.0.0/16")
                .defaultInstanceTenancy(DefaultInstanceTenancy.DEFAULT)
                .enableDnsSupport(true)
                .enableDnsHostnames(true)
                .maxAzs(2)
                .build();
    }

    private DatabaseInstance createRds(Vpc vpc) {

        // CODE to create a dynamodDb table.
//        TableProps tableProps;
//        val partitionKey = Attribute.builder()
//                .name("itemId")
//                .type(AttributeType.STRING)
//                .build();
//        tableProps = TableProps.builder()
//                .tableName("items")
//                .partitionKey(partitionKey)
//                // The default removal policy is RETAIN, which means that cdk destroy will not attempt to delete
//                // the new table, and it will remain in your account until manually deleted. By setting the policy to
//                // DESTROY, cdk destroy will delete the table (even if it has data in it)
//                .removalPolicy(RemovalPolicy.DESTROY)
//                .build();
//        return new Table(this, "SpringPetclinicDB", tableProps);

//        rdsInst = rds.DatabaseInstance(self, 'SpringPetclinicDB',
//                engine=rds.DatabaseInstanceEngine.MYSQL,
//                engine_version='5.7.31',
//                instance_class=ec2.InstanceType('t2.medium'),
//                master_username = 'master',
//                database_name = 'petclinic',
//                master_user_password = core.SecretValue('Welcome#123456'),
//                vpc = vpc,
//                deletion_protection = False,
//                backup_retention = core.Duration.days(0),
//                removal_policy = core.RemovalPolicy.DESTROY,
//                #vpc_placement = ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC)
//        )

//        rdsInst.connections.allow_default_port_from_any_ipv4()


        return new DatabaseInstance(this, "SpringPetclinicDB",
                DatabaseInstanceProps.builder()
                        .engine(DatabaseInstanceEngine.MYSQL)
                        .instanceType(new InstanceType("t2.micro"))
                        .databaseName("petclinic")
                        .credentials(Credentials.fromPassword("master", new SecretValue("Welcome#123456")))
                        .vpc(vpc)
                        .deletionProtection(false)
                        .backupRetention(Duration.days(0))
                        .removalPolicy(RemovalPolicy.DESTROY)
                        .build()
        );
    }


}
