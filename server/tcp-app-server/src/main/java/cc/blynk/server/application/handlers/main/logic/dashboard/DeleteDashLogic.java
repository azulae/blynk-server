package cc.blynk.server.application.handlers.main.logic.dashboard;

import cc.blynk.server.Holder;
import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.dao.TokenManager;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.redis.RedisClient;
import cc.blynk.utils.ArrayUtil;
import cc.blynk.utils.ParseUtil;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.utils.BlynkByteBufUtil.ok;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class DeleteDashLogic {

    private static final Logger log = LogManager.getLogger(DeleteDashLogic.class);

    private final TokenManager tokenManager;
    private final BlockingIOProcessor blockingIOProcessor;
    private final RedisClient redisClient;

    public DeleteDashLogic(Holder holder) {
        this.tokenManager = holder.tokenManager;
        this.blockingIOProcessor = holder.blockingIOProcessor;
        this.redisClient = holder.redisClient;
    }

    public void messageReceived(ChannelHandlerContext ctx, User user, StringMessage message) {
        int dashId = ParseUtil.parseInt(message.body);

        int index = user.profile.getDashIndexOrThrow(dashId);

        log.debug("Deleting dashboard {}.", dashId);

        DashBoard dash = user.profile.dashBoards[index];

        user.recycleEnergy(dash.energySum());

        //if last project and we have less than 1000 fill up to 1000
        if (user.profile.dashBoards.length == 1 && user.getEnergy() < 1000 && user.getEnergy() >= 0) {
            user.purchaseEnergy(1000 - user.getEnergy());
        }

        user.profile.dashBoards = ArrayUtil.remove(user.profile.dashBoards, index);
        String removedToken = tokenManager.deleteProject(user, dashId);

        if (removedToken != null) {
            blockingIOProcessor.execute(() -> {
                redisClient.removeToken(removedToken);
            });
        }

        user.lastModifiedTs = System.currentTimeMillis();

        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }

}
