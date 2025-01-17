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

import java.util.concurrent.CompletableFuture;

public interface RaftMessageSender {

    /**
     * Add a new server to the cluster
     * @param server new member of cluster
     * @return true if request is accepted, or false if no leader, rpc fails or leader declines
     */
    //CompletableFuture<Boolean> addServer(ClusterServer server);

    /**
     * Remove a server from cluster, the server will step down when the removal is confirmed
     * @param serverId the id for the server to be removed
     * @return true if request is accepted or false if no leader, rpc fails or leader declines
     */
    //CompletableFuture<Boolean> removeServer(int serverId);

    /**
     * Append multiple application logs to log store
     * @param values the application log entries
     * @return true if request is accepted or false if no leader, rpc fails or leader declines
     */
    CompletableFuture<Boolean> appendEntries(byte[][] values);
}
