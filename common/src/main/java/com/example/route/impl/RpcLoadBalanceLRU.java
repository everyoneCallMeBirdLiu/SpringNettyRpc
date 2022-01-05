package com.example.route.impl;


import com.example.core.RpcProtocol;
import com.example.core.client.RpcClientHandler;
import com.example.route.RpcLoadBalance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * LRU load balance
 * Created by luxiaoxun on 2020-08-01.
 */
public class RpcLoadBalanceLRU extends RpcLoadBalance {
    private ConcurrentMap<String, LinkedHashMap<RpcProtocol, RpcProtocol>> jobLRUMap =
            new ConcurrentHashMap<String, LinkedHashMap<RpcProtocol, RpcProtocol>>();
    private long CACHE_VALID_TIME = 0;

    public RpcProtocol doRoute(String serviceKey, List<RpcProtocol> addressList) {
        // cache clear
        if (System.currentTimeMillis() > CACHE_VALID_TIME) {
            jobLRUMap.clear();
            CACHE_VALID_TIME = System.currentTimeMillis() + 1000 * 60 * 60 * 24;
        }

        // init lru
        LinkedHashMap<RpcProtocol, RpcProtocol> lruHashMap = jobLRUMap.get(serviceKey);
        if (lruHashMap == null) {
            /**
             * LinkedHashMap
             * a、accessOrder：ture=访问顺序排序（get/put时排序）/ACCESS-LAST；false=插入顺序排期/FIFO；
             * b、removeEldestEntry：新增元素时将会调用，返回true时会删除最老元素；
             *      可封装LinkedHashMap并重写该方法，比如定义最大容量，超出是返回true即可实现固定长度的LRU算法；
             */
            lruHashMap = new LinkedHashMap<RpcProtocol, RpcProtocol>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RpcProtocol, RpcProtocol> eldest) {
                    if (super.size() > 1000) {
                        return true;
                    } else {
                        return false;
                    }
                }
            };
            jobLRUMap.putIfAbsent(serviceKey, lruHashMap);
        }

        // put new
        for (RpcProtocol address : addressList) {
            if (!lruHashMap.containsKey(address)) {
                lruHashMap.put(address, address);
            }
        }
        // remove old
        List<RpcProtocol> delKeys = new ArrayList<>();
        for (RpcProtocol existKey : lruHashMap.keySet()) {
            if (!addressList.contains(existKey)) {
                delKeys.add(existKey);
            }
        }
        if (delKeys.size() > 0) {
            for (RpcProtocol delKey : delKeys) {
                lruHashMap.remove(delKey);
            }
        }

        // load
        RpcProtocol eldestKey = lruHashMap.entrySet().iterator().next().getKey();
        RpcProtocol eldestValue = lruHashMap.get(eldestKey);
        return eldestValue;
    }

    @Override
    public RpcProtocol route(String serviceKey, Map<String, RpcClientHandler> connectedServerNodes, Map<String, RpcProtocol> rpcProtocolMap) throws Exception {
        Map<String, List<RpcProtocol>> serviceMap = getServiceMap(connectedServerNodes, rpcProtocolMap);
        List<RpcProtocol> addressList = serviceMap.get(serviceKey);
        if (addressList != null && addressList.size() > 0) {
            return doRoute(serviceKey, addressList);
        } else {
            throw new Exception("Can not find connection for service: " + serviceKey);
        }
    }
}
