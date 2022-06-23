package bot.entities;

import java.util.Objects;

public class ChallengeEntity
{
    private final Long challenged;
    private final Long challenger;

    public ChallengeEntity(Long challenged, Long challenger) {
        this.challenged = challenged;
        this.challenger = challenger;
    }

    public Long getChallenged() {
        return challenged;
    }

    public Long getChallenger() {
        return challenger;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChallengeEntity that = (ChallengeEntity) o;
        return Objects.equals(challenged, that.challenged) && Objects.equals(challenger, that.challenger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(challenged, challenger);
    }
}
