package com.demo.scheduler.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KafkaAdminService {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${scheduler.broker.topic}")
    private String topicName;

    private AdminClient adminClient;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        this.adminClient = AdminClient.create(props);
        log.info("KafkaAdminService initialized with bootstrap servers: {}", bootstrapServers);
    }

    @PreDestroy
    public void cleanup() {
        if (adminClient != null) {
            adminClient.close();
        }
    }

    /**
     * Get cluster information including brokers and controller
     */
    public Map<String, Object> getClusterInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            DescribeClusterResult cluster = adminClient.describeCluster();
            
            Collection<Node> nodes = cluster.nodes().get(5, TimeUnit.SECONDS);
            Node controller = cluster.controller().get(5, TimeUnit.SECONDS);
            String clusterId = cluster.clusterId().get(5, TimeUnit.SECONDS);

            List<Map<String, Object>> brokers = nodes.stream().map(node -> {
                Map<String, Object> broker = new HashMap<>();
                broker.put("id", node.id());
                broker.put("host", node.host());
                broker.put("port", node.port());
                broker.put("isController", node.id() == controller.id());
                return broker;
            }).collect(Collectors.toList());

            result.put("clusterId", clusterId);
            result.put("brokers", brokers);
            result.put("controllerBrokerId", controller.id());
            result.put("status", "CONNECTED");

        } catch (Exception e) {
            log.error("Failed to get cluster info", e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Get topic details including partitions and offsets
     */
    public Map<String, Object> getTopicInfo(String topic) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Describe topic
            DescribeTopicsResult topicsResult = adminClient.describeTopics(Collections.singletonList(topic));
            TopicDescription description = topicsResult.topicNameValues().get(topic).get(5, TimeUnit.SECONDS);

            List<Map<String, Object>> partitions = new ArrayList<>();
            List<TopicPartition> topicPartitions = new ArrayList<>();

            for (TopicPartitionInfo partitionInfo : description.partitions()) {
                Map<String, Object> partition = new HashMap<>();
                partition.put("partition", partitionInfo.partition());
                partition.put("leader", partitionInfo.leader() != null ? partitionInfo.leader().id() : -1);
                partition.put("replicas", partitionInfo.replicas().stream().map(Node::id).collect(Collectors.toList()));
                partition.put("isr", partitionInfo.isr().stream().map(Node::id).collect(Collectors.toList()));
                partitions.add(partition);
                topicPartitions.add(new TopicPartition(topic, partitionInfo.partition()));
            }

            // Get end offsets (latest offsets)
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = 
                adminClient.listOffsets(topicPartitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest())))
                    .all().get(5, TimeUnit.SECONDS);

            // Get beginning offsets
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> beginOffsets = 
                adminClient.listOffsets(topicPartitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.earliest())))
                    .all().get(5, TimeUnit.SECONDS);

            // Add offsets to partition info
            for (Map<String, Object> partition : partitions) {
                int partitionId = (int) partition.get("partition");
                TopicPartition tp = new TopicPartition(topic, partitionId);
                partition.put("beginOffset", beginOffsets.get(tp).offset());
                partition.put("endOffset", endOffsets.get(tp).offset());
                partition.put("messageCount", endOffsets.get(tp).offset() - beginOffsets.get(tp).offset());
            }

            result.put("topic", topic);
            result.put("partitionCount", description.partitions().size());
            result.put("partitions", partitions);
            result.put("isInternal", description.isInternal());

        } catch (Exception e) {
            log.error("Failed to get topic info for: {}", topic, e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Get consumer group details including lag
     */
    public Map<String, Object> getConsumerGroupInfo(String groupId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Describe consumer group
            DescribeConsumerGroupsResult groupsResult = adminClient.describeConsumerGroups(Collections.singletonList(groupId));
            ConsumerGroupDescription description = groupsResult.describedGroups().get(groupId).get(5, TimeUnit.SECONDS);

            result.put("groupId", groupId);
            result.put("state", description.state().toString());
            result.put("coordinator", description.coordinator() != null ? description.coordinator().host() + ":" + description.coordinator().port() : "N/A");

            // Get member info
            List<Map<String, Object>> members = description.members().stream().map(member -> {
                Map<String, Object> memberInfo = new HashMap<>();
                memberInfo.put("memberId", member.consumerId());
                memberInfo.put("clientId", member.clientId());
                memberInfo.put("host", member.host());
                memberInfo.put("partitions", member.assignment().topicPartitions().stream()
                    .map(tp -> tp.topic() + "-" + tp.partition())
                    .collect(Collectors.toList()));
                return memberInfo;
            }).collect(Collectors.toList());
            result.put("members", members);

            // Get committed offsets
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);

            // Calculate lag
            List<Map<String, Object>> partitionLag = new ArrayList<>();
            long totalLag = 0;

            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                long committedOffset = entry.getValue().offset();

                // Get end offset
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffset = 
                    adminClient.listOffsets(Collections.singletonMap(tp, OffsetSpec.latest()))
                        .all().get(5, TimeUnit.SECONDS);

                long latestOffset = endOffset.get(tp).offset();
                long lag = latestOffset - committedOffset;
                totalLag += lag;

                Map<String, Object> partitionInfo = new HashMap<>();
                partitionInfo.put("topic", tp.topic());
                partitionInfo.put("partition", tp.partition());
                partitionInfo.put("committedOffset", committedOffset);
                partitionInfo.put("latestOffset", latestOffset);
                partitionInfo.put("lag", lag);
                partitionLag.add(partitionInfo);
            }

            result.put("partitions", partitionLag);
            result.put("totalLag", totalLag);

        } catch (Exception e) {
            log.error("Failed to get consumer group info for: {}", groupId, e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getConsumerGroupId() {
        return consumerGroupId;
    }
}
