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

import com.google.common.net.HostAndPort;

public class RaftConsensus {

    public static RaftMessageSender run(RaftContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }

        RaftServer server = new RaftServer(context);
        RaftMessageSender messageSender = server.createMessageSender();
        context.getStateMachine().start(messageSender);
        context.getRpcListener().startListening(server);
        server.startRapid();

        return messageSender;
    }
}
