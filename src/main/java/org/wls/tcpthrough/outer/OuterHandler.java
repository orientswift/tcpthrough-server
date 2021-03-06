package org.wls.tcpthrough.outer;

import io.netty.channel.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wls.tcpthrough.Tools;
import org.wls.tcpthrough.model.ConnectModel;
import org.wls.tcpthrough.model.GlobalObject;
import org.wls.tcpthrough.model.ManagerProtocolBuf.ManagerResponse;
import org.wls.tcpthrough.model.ManagerProtocolBuf.RegisterProtocol;
import org.wls.tcpthrough.model.ResponseType;
import org.wls.tcpthrough.model.TrustIpsModel;

import java.net.Inet4Address;
import java.net.InetSocketAddress;


/**
 * Created by wls on 2019/10/15.
 */
public class OuterHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LogManager.getLogger(OuterHandler.class);

    private boolean isBind = false;
    private String channelId;
    private GlobalObject globalObject;
    private Channel dataChannel;
    public Channel manageChannel;
    private RegisterProtocol registerProtocol;
    private TrustIpsModel trustIpsModel;

    public OuterHandler(Channel manageChannel, GlobalObject globalObject, RegisterProtocol registerProtocol, TrustIpsModel trustIpsModel){
        this.manageChannel = manageChannel;
        this.globalObject = globalObject;
        this.registerProtocol = registerProtocol;
        this.trustIpsModel = trustIpsModel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOG.info("Outer server has a new connection" + ctx.channel().remoteAddress());

        if(registerProtocol.getIsAuth()){
            InetSocketAddress inetAddr = (InetSocketAddress)ctx.channel().remoteAddress();
            if(!trustIpsModel.contains(inetAddr)){
                ctx.channel().close();
                LOG.warn("The remote ip " + inetAddr + " is not in the trust ips list");
                return;
            }
        }

        channelId = Tools.uuid();
        while (globalObject.isOuterChannelUUIDExist(channelId)) {
            LOG.warn("The uuid " +channelId +" is repeat.");
            channelId = Tools.uuid();
        }

        ConnectModel connectModel = new ConnectModel(channelId, this, ctx.channel());
        globalObject.putOuterConnection(channelId, connectModel);

        //One manage channel may operate multi register,so add proxy port to tell manage client.
        String protocolChannelId = registerProtocol.getRemoteProxyPort() + ":" + channelId;

        ManagerResponse response = ManagerResponse.newBuilder()
                .setType(ResponseType.NEW_CONN_RESPONSE.get())
                .setValue(protocolChannelId)
                .setValueMd5(Tools.getMD5(protocolChannelId))
                .build();

        manageChannel.writeAndFlush(response).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.isSuccess()){
                    LOG.debug("Data write manage client successfully");
                } else {
                    LOG.error("Data write manage client error");
                    ctx.channel().close();
                    globalObject.deleteOuterConnection(channelId);
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().close();
        if(channelId != null){
            globalObject.deleteOuterConnection(channelId);
        }
        if (dataChannel != null) {
            dataChannel.close();
        }

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        dataChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    LOG.error("Out data write to inner data client error");
                    dataChannel.close();
                    ctx.channel().close();
                }
            }
        });
    }

//    @Override
//    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.error("", cause);
        ctx.channel().close();
        globalObject.deleteOuterConnection(channelId);
        if (dataChannel != null) {
            dataChannel.close();
        }
    }



    public void setDataChannel(Channel dataChannel) {
        this.dataChannel = dataChannel;
    }

    public RegisterProtocol getRegisterProtocol() {
        return registerProtocol;
    }

}
