package bot.dao;

import bot.entities.ChallengeEntity;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

public class ChallengeDao
{
    private final Map<ChallengeEntity, Timer> challenges = new ConcurrentHashMap<>();

    public void saveChallenge(Long challenged, Long challenger, Timer onExpiry) {
        ChallengeEntity challengeEntity = new ChallengeEntity(challenged, challenger);
        challenges.put(challengeEntity, onExpiry);
    }

    @Nullable
    public Timer getChallengeTimer(Long challenged, Long challenger) {
        ChallengeEntity challengeEntity = new ChallengeEntity(challenged, challenger);
        return challenges.get(challengeEntity);
    }

    @Nullable
    public Timer deleteChallenge(Long challenged, Long challenger) {
        ChallengeEntity challengeEntity = new ChallengeEntity(challenged, challenger);
        return challenges.remove(challengeEntity);
    }
}
