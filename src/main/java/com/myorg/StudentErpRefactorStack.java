package com.myorg;

import lombok.val;
import software.amazon.awscdk.*;
import software.amazon.awscdk.IResource;
import software.amazon.awscdk.services.apigateway.*;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentErpRefactorStack extends Stack {
    public StudentErpRefactorStack(final Construct parent, final String id) {
        this(parent, id, null);
    }

    public StudentErpRefactorStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

        val vpc = createVpc();
        val dockerImageAsset = createDockerImageAsset();
//
        val cluster = createCluster(vpc);
//
//        val rds = createRds(vpc);
        val loadBalancer = createLoadBalancedEc2Service(cluster, dockerImageAsset, null);

    }

    private ApplicationLoadBalancedEc2Service createLoadBalancedEc2Service(Cluster cluster, DockerImageAsset dockerImageAsset,
                                                                           DatabaseInstance rds) {
        return new ApplicationLoadBalancedEc2Service(this, "Ec2Service",
                ApplicationLoadBalancedEc2ServiceProps.builder()
                        .cluster(cluster)
                        .memoryLimitMiB(512)
                        .serviceName("spring-petclinic")
                        .desiredCount(1)
                        .publicLoadBalancer(true)
                        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(ContainerImage.fromDockerImageAsset(dockerImageAsset))
                                .containerName("spring-petclinic")
                                .containerPort(8000)
                                .environment(new HashMap<String, String>() {{
                                    put("SPRING_DATASOURCE_PASSWORD", "Welcome#12345");
                                    put("SPRING_DATASOURCE_USERNAME", "");
                                    put("SPRING_PROFILES_ACTIVE", "");
//                                    put("SPRING_DATASOURCE_URL", "jdbc:mysql://" + rds.getDbInstanceEndpointAddress() + "/petclinic?useUnicode=true&enabledTLSProtocols=TLSv1.2");
                                }})
                                .build())
                        .build());
    }

//    private void addCorsOptions(IResource item) {
//        List<MethodResponse> methoedResponses = new ArrayList<>();
//
//        Map<String, Boolean> responseParameters = new HashMap<>();
//        responseParameters.put("method.response.header.Access-Control-Allow-Headers", Boolean.TRUE);
//        responseParameters.put("method.response.header.Access-Control-Allow-Methods", Boolean.TRUE);
//        responseParameters.put("method.response.header.Access-Control-Allow-Credentials", Boolean.TRUE);
//        responseParameters.put("method.response.header.Access-Control-Allow-Origin", Boolean.TRUE);
//        methoedResponses.add(MethodResponse.builder()
//                .responseParameters(responseParameters)
//                .statusCode("200")
//                .build());
//        MethodOptions methodOptions = MethodOptions.builder()
//                .methodResponses(methoedResponses)
//                .build()
//                ;
//
//        Map<String, String> requestTemplate = new HashMap<>();
//        requestTemplate.put("application/json","{\"statusCode\": 200}");
//        List<IntegrationResponse> integrationResponses = new ArrayList<>();
//
//        Map<String, String> integrationResponseParameters = new HashMap<>();
//        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Headers","'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,X-Amz-User-Agent'");
//        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Origin","'*'");
//        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Credentials","'false'");
//        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Methods","'OPTIONS,GET,PUT,POST,DELETE'");
//        integrationResponses.add(IntegrationResponse.builder()
//                .responseParameters(integrationResponseParameters)
//                .statusCode("200")
//                .build());
//        Integration methodIntegration = MockIntegration.Builder.create()
//                .integrationResponses(integrationResponses)
//                .passthroughBehavior(PassthroughBehavior.NEVER)
//                .requestTemplates(requestTemplate)
//                .build();
//        item.addMethod("OPTIONS", methodIntegration, methodOptions);
//    }

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
                .subnetConfiguration(List.of(
                        SubnetConfiguration.builder().mapPublicIpOnLaunch(true).cidrMask(24).subnetType(SubnetType.PUBLIC).name("public-one").build(),
                        SubnetConfiguration.builder().cidrMask(24).subnetType(SubnetType.PRIVATE_ISOLATED).name("private-one").build()
                ))
                .defaultInstanceTenancy(DefaultInstanceTenancy.DEFAULT)
                .enableDnsSupport(true)
                .enableDnsHostnames(true)
                .maxAzs(1)
//                .natGateways(0)
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
