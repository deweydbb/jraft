/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  The ASF licenses
 * this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.data.technology.jraft;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RaftClient {

    private final Map<Integer, RpcClient> rpcClients = new HashMap<Integer, RpcClient>();
    private final RpcClientFactory rpcClientFactory;
    private ClusterConfiguration configuration;
    private final Logger logger;
    private final Timer timer;
    private int leaderId;
    private boolean randomLeader;
    private final Random random;

    public RaftClient(RpcClientFactory rpcClientFactory, ClusterConfiguration configuration, LoggerFactory loggerFactory) {
        this.random = new Random(Calendar.getInstance().getTimeInMillis());
        this.rpcClientFactory = rpcClientFactory;
        this.configuration = configuration;
        this.leaderId = configuration.getServers().get(this.random.nextInt(configuration.getServers().size())).getId();
        this.randomLeader = true;
        this.logger = loggerFactory.getLogger(getClass());
        this.timer = new Timer();
    }

    public CompletableFuture<Boolean> appendEntries(byte[][] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values cannot be null or empty");
        }

        LogEntry[] logEntries = new LogEntry[values.length];
        for (int i = 0; i < values.length; ++i) {
            logEntries[i] = new LogEntry(0, values[i]);
        }

        RaftRequestMessage request = new RaftRequestMessage();
        request.setMessageType(RaftMessageType.ClientRequest);
        request.setLogEntries(logEntries);

        CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();
        this.tryCurrentLeader(request, result, 0, 0);
        return result;
    }

    public CompletableFuture<ClusterConfiguration> getClusterConfig() {
        RaftRequestMessage request = new RaftRequestMessage();
        request.setMessageType(RaftMessageType.GetClusterRequest);

        CompletableFuture<ClusterConfiguration> result = new CompletableFuture<>();
        tryAnyNode(request, result);
        return result;
    }

    public int getLeaderId() {
        return randomLeader ? -1 : leaderId;
    }

    private void tryAnyNode(RaftRequestMessage request, CompletableFuture<ClusterConfiguration> future) {
        tryAnyNode(request, future, configuration.getServers().iterator());
    }

    private void tryAnyNode(RaftRequestMessage request, CompletableFuture<ClusterConfiguration> future, Iterator<ClusterServer> iterator) {
        if (iterator.hasNext()) {
            int serverId = iterator.next().getId();
            RpcClient client = getOrCreateRpcClient(serverId);

            client.send(request).whenCompleteAsync((RaftResponseMessage response, Throwable error) -> {
                if (error == null) {
                    logger.debug("response from remote server, leader: %d, accepted: %s", response.getDestination(), String.valueOf(response.isAccepted()));
                    if (response.isAccepted()) {
                        LogEntry[] logEntries = response.getLogEntries();
                        if (logEntries != null &&
                                logEntries.length == 1 &&
                                logEntries[0].getValueType() == LogValueType.Configuration) {
                            configuration = ClusterConfiguration.fromBytes(logEntries[0].getValue());
                            leaderId = response.getDestination();
                            randomLeader = false;
                            future.complete(configuration);
                        } else {
                            future.complete(null);
                        }

                    } else {
                        tryAnyNode(request, future, iterator);
                    }
                } else {
                    logger.info("rpc error, failed to send request to remote server (%s)", error.getMessage());
                    tryAnyNode(request, future, iterator);
                }
            });
        } else {
            future.complete(null);
        }
    }

//    public CompletableFuture<Boolean> addServer(ClusterServer server) {
//        if (server == null) {
//            throw new IllegalArgumentException("server cannot be null");
//        }
//
//        LogEntry[] logEntries = new LogEntry[1];
//        logEntries[0] = new LogEntry(0, server.toBytes(), LogValueType.ClusterServer);
//        RaftRequestMessage request = new RaftRequestMessage();
//        request.setMessageType(RaftMessageType.AddServerRequest);
//        request.setLogEntries(logEntries);
//
//        CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();
//        this.tryCurrentLeader(request, result, 0, 0);
//        return result;
//    }
//
//    public CompletableFuture<Boolean> removeServer(int serverId) {
//        if (serverId < 0) {
//            throw new IllegalArgumentException("serverId must be equal or greater than zero");
//        }
//
//        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
//        buffer.putInt(serverId);
//        LogEntry[] logEntries = new LogEntry[1];
//        logEntries[0] = new LogEntry(0, buffer.array(), LogValueType.ClusterServer);
//        RaftRequestMessage request = new RaftRequestMessage();
//        request.setMessageType(RaftMessageType.RemoveServerRequest);
//        request.setLogEntries(logEntries);
//
//        CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();
//        this.tryCurrentLeader(request, result, 0, 0);
//        return result;
//    }

    private void tryCurrentLeader(RaftRequestMessage request, CompletableFuture<Boolean> future, int rpcBackoff, int retry) {
        logger.debug("trying request to %d as current leader", this.leaderId);
        getOrCreateRpcClient().send(request).whenCompleteAsync((RaftResponseMessage response, Throwable error) -> {
            if (error == null) {
                logger.debug("response from remote server, leader: %d, accepted: %s", response.getDestination(), String.valueOf(response.isAccepted()));
                if (response.isAccepted()) {
                    this.randomLeader = false;
                    future.complete(true);
                } else {
                    // set the leader return from the server
                    if (this.leaderId == response.getDestination() && !this.randomLeader) {
                        future.complete(false);
                    } else {
                        this.randomLeader = false;
                        this.leaderId = response.getDestination();
                        tryCurrentLeader(request, future, rpcBackoff, retry);
                    }
                }
            } else {
                logger.info("rpc error, failed to send request to remote server (%s)", error.getMessage());
                if (retry > configuration.getServers().size()) {
                    future.complete(false);
                    return;
                }

                // try a random server as leader
                this.leaderId = this.configuration.getServers().get(this.random.nextInt(this.configuration.getServers().size())).getId();
                this.randomLeader = true;
                refreshRpcClient();

                if (rpcBackoff > 0) {
                    timer.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            tryCurrentLeader(request, future, rpcBackoff + 50, retry + 1);

                        }
                    }, rpcBackoff);
                } else {
                    tryCurrentLeader(request, future, rpcBackoff + 50, retry + 1);
                }
            }
        });
    }

    private RpcClient getOrCreateRpcClient() {
        synchronized (this.rpcClients) {
            if (this.rpcClients.containsKey(this.leaderId)) {
                return this.rpcClients.get(this.leaderId);
            }

            RpcClient client = this.rpcClientFactory.createRpcClient(getLeaderEndpoint());
            this.rpcClients.put(this.leaderId, client);
            return client;
        }
    }

    private RpcClient getOrCreateRpcClient(int serverId) {
        synchronized (this.rpcClients) {
            if (this.rpcClients.containsKey(serverId)) {
                return this.rpcClients.get(serverId);
            }

            RpcClient client = this.rpcClientFactory.createRpcClient(getEndpoint(serverId));
            this.rpcClients.put(serverId, client);
            return client;
        }
    }

    private RpcClient refreshRpcClient() {
        synchronized (this.rpcClients) {
            RpcClient client = this.rpcClientFactory.createRpcClient(getLeaderEndpoint());
            this.rpcClients.put(this.leaderId, client);
            return client;
        }
    }

    private String getEndpoint(int serverId) {
        for (ClusterServer server : this.configuration.getServers()) {
            if (server.getId() == serverId) {
                return server.getEndpoint();
            }
        }

        return null;
    }

    private String getLeaderEndpoint() {
        logger.info("config in get leader endpoint: %s", configuration.getServers());
        for (ClusterServer server : this.configuration.getServers()) {
            logger.info("Searching for leader %d, found endpoint: %d", leaderId, server.getId());
            if (server.getId() == this.leaderId) {
                return server.getEndpoint();
            }
        }

        try {
            // try and re-get the configuration to see if the leader is in the new config
            configuration = getClusterConfig().get();
            logger.info("config in get leader endpoint: %s", configuration.getServers());
            for (ClusterServer server : this.configuration.getServers()) {
                logger.info("Searching for leader %d, found endpoint: %d", leaderId, server.getId());
                if (server.getId() == this.leaderId) {
                    return server.getEndpoint();
                }
            }
        } catch (Exception ignored) {}

        logger.info("no endpoint could be found for leader %d, that usually means no leader is elected, retry the first one", this.leaderId);
        this.randomLeader = true;
        this.leaderId = this.configuration.getServers().get(0).getId();
        return this.configuration.getServers().get(0).getEndpoint();
    }

    public void setConfiguration(ClusterConfiguration configuration) {
        this.configuration = configuration;
    }
}
