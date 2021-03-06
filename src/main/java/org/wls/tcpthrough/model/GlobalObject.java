package org.wls.tcpthrough.model;

import io.netty.channel.Channel;
import org.wls.tcpthrough.outer.OuterServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GlobalObject {

    private String securityKey;

    public String getSecurityKey() {
        return securityKey;
    }

    public void setSecurityKey(String securityKey) {
        this.securityKey = securityKey;
    }

    //the map is used to find ConnectModel for channel id
    private Map<String, ConnectModel> outConnectionMap;

    //It used to find one manage channel mapping outer server list.
    // when the manage client is down , we should use the mapping to close the outer server
    // channle is the manage channel
    private Map<Channel, List<OuterServer>> channelMapping;

    private Map<String, Channel> clientNames;
    private Lock nameLock = new ReentrantLock();


    public GlobalObject() {
        outConnectionMap = new ConcurrentHashMap<>();
        channelMapping = new ConcurrentHashMap<>();
        clientNames = new ConcurrentHashMap<>();
    }

    public boolean registerName(String name, Channel channel){
        nameLock.lock();
        try{
            if(clientNames.containsKey(name)){
                return false;
            } else {
                clientNames.put(name, channel);
                return true;
            }
        } finally {
            nameLock.unlock();
        }
    }

    public Channel getManageChannelByName(String name){
        return clientNames.get(name);
    }

    public void removeName(String name){
        clientNames.remove(name);
    }

    public boolean isOuterChannelUUIDExist(String uuid) {
        if (outConnectionMap.containsKey(uuid)) {
            return true;
        } else {
            return false;
        }
    }

    public List<ConnectModel> getConnectList(){
        return new ArrayList<>(outConnectionMap.values());
    }

    public void putOuterConnection(String uuid, ConnectModel connectModel) {
        outConnectionMap.put(uuid, connectModel);
    }

    public void deleteOuterConnection(String uuid) {
        outConnectionMap.remove(uuid);
    }

    public ConnectModel getOuterConnection(String uuid) {
        return outConnectionMap.get(uuid);
    }


    public void putChannelOuterServer(Channel channel, OuterServer outerServer) {
        if (!channelMapping.containsKey(channel)) {
            channelMapping.put(channel, new ArrayList<OuterServer>());
        }
        channelMapping.get(channel).add(outerServer);
    }

    public List<OuterServer> getChannelOuterServer(Channel channel) {
        return channelMapping.get(channel);
    }


    public List<List<OuterServer>> getAllOuterServers(){
        return new ArrayList<>(channelMapping.values());
    }


    public void deleteChannelOuterServerList(Channel channel) {
        if (channelMapping.containsKey(channel)) {
            channelMapping.remove(channel);
        }
    }

    public void deleteChannelSingleOuterServer(Channel channel, OuterServer outerServer) {
        if (channelMapping.containsKey(channel)) {
            channelMapping.get(channel).removeIf(os -> os.getRegisterProtocol().getRemoteProxyPort() == (outerServer.getRegisterProtocol().getRemoteProxyPort()));
        }
    }

    public Map<Channel, List<OuterServer>> getChannelOutServerMapping(){
        return channelMapping;
    }


}
