package com.myorg;

import lombok.val;
import software.amazon.awscdk.*;
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

import java.util.HashMap;

public class StudentErpRefactorStack extends Stack {
    public StudentErpRefactorStack(final Construct parent, final String id) {
        this(parent, id, null);
    }

    public StudentErpRefactorStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

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
                                    put("SPRING_DATASOURCE_PASSWORD", "Welcome#12345");
                                    put("SPRING_DATASOURCE_USERNAME", "");
                                    put("SPRING_PROFILES_ACTIVE", "");
                                    put("SPRING_DATASOURCE_URL", "jdbc:mysql://" + rds.getDbInstanceEndpointAddress() + "/petclinic?useUnicode=true&enabledTLSProtocols=TLSv1.2");
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
