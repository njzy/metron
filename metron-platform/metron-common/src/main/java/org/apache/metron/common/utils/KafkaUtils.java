/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.metron.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum KafkaUtils {
  INSTANCE;
  public List<String> getBrokersFromZookeeper(String zkQuorum) throws Exception {
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
    CuratorFramework framework = CuratorFrameworkFactory.newClient(zkQuorum, retryPolicy);
    framework.start();
    try {
      return getBrokersFromZookeeper(framework);
    } finally {
      framework.close();
    }
  }

  public List<String> getBrokersFromZookeeper(CuratorFramework client) throws Exception {
    List<String> ret = new ArrayList<>();
    for(String id : client.getChildren().forPath("/brokers/ids")) {
      byte[] data = client.getData().forPath("/brokers/ids/" + id);
      String brokerInfoStr = new String(data);
      Map<String, Object> brokerInfo = JSONUtils.INSTANCE.load(brokerInfoStr, new TypeReference<Map<String, Object>>() {
      });
      String host = (String) brokerInfo.get("host");
      if(host != null) {
        ret.add(host + ":" + brokerInfo.get("port"));
      }
      else {
        Object endpoints = brokerInfo.get("endpoints");
        if(endpoints != null && endpoints instanceof List) {
          List<String> eps = (List<String>)endpoints;
          for(String url : eps) {
            ret.addAll(fromEndpoint(url));
          }
        }
      }
    }
    return ret;
  }

  /*
  The URL accepted is NOT a general URL, and is assumed to follow the format used by the Kafka structures in Zookeeper.
  See: https://cwiki.apache.org/confluence/display/KAFKA/Kafka+data+structures+in+Zookeeper
   */
  List<String> fromEndpoint(String url){
    List<String> ret = new ArrayList<>();
    if(url != null) {
      Iterable<String> splits = Splitter.on("//").split(url);
      if(Iterables.size(splits) == 2) {
        String hostPort = Iterables.getLast(splits);
        ret.add(hostPort);
      }
    }
    return ret;
  }
}