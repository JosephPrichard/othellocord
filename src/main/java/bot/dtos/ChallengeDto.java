package bot.dtos;

public class ChallengeDto
{
    private PlayerDto challenged;
    private PlayerDto challenger;

    public ChallengeDto(PlayerDto challenged, PlayerDto challenger) {
        this.challenged = challenged;
        this.challenger = challenger;
    }

    public PlayerDto getChallenger() {
        return challenger;
    }

    public void setChallenger(PlayerDto challenger) {
        this.challenger = challenger;
    }

    public PlayerDto getChallenged() {
        return challenged;
    }

    public void setChallenged(PlayerDto challenged) {
        this.challenged = challenged;
    }
}
