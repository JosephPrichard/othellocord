package bot.services;

import bot.dao.ChallengeDao;
import bot.dtos.ChallengeDto;
import bot.dtos.PlayerDto;
import bot.entities.ChallengeEntity;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ChallengeService
{
    public static final long EXPIRY = 1000 * 30;

    private final Logger logger = Logger.getLogger("service.challenge");
    private final ChallengeDao challengeDao;

    public ChallengeService(ChallengeDao challengeDao) {
        this.challengeDao = challengeDao;
    }

    public void createChallenge(ChallengeDto challenge, Runnable onExpiry) {
        Long challengedId = challenge.getChallenged().getId();
        Long challengerId = challenge.getChallenger().getId();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                onExpiry.run();
                challengeDao.deleteChallenge(challengedId, challengerId);
                logger.info("Challenge expired " + challengedId + " " + challengerId);
            }
        };
        Timer timer = new Timer("Timer");
        timer.schedule(task, EXPIRY);

        challengeDao.saveChallenge(challengedId, challengerId, timer);
    }

    public boolean acceptChallenge(ChallengeDto challenge) {
        Long challengedId = challenge.getChallenged().getId();
        Long challengerId = challenge.getChallenger().getId();
        Timer timer = challengeDao.deleteChallenge(challengedId, challengerId);
        if (timer != null) {
            timer.cancel();
            return true;
        }
        return false;
    }
}
