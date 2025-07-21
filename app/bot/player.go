package bot

import (
	"context"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"github.com/jellydator/ttlcache/v3"
	"log/slog"
	"strconv"
	"time"
)

const MinBotLevel = 1
const MaxBotLevel = 10

type Player struct {
	ID   string
	Name string
}

func PlayerFromUser(user *discordgo.User) Player {
	if user == nil {
		panic("expected user to be non nil when creating player")
	}
	return Player{ID: user.ID, Name: user.Username}
}

func GetBotName(playerId string) string {
	if id, err := strconv.Atoi(playerId); err == nil && IsValidBotLevel(id) {
		return GetBotLevelFmt(id)
	}
	return ""
}

func (player Player) isBot() bool {
	return GetBotName(player.ID) != ""
}

func IsValidBotLevel(level int) bool {
	return level >= MinBotLevel && level <= MaxBotLevel
}

func GetBotLevelFmt(level int) string {
	return fmt.Sprintf("OthelloBot level %d", level)
}

func GetBotLevel(playerId string) int {
	id, _ := strconv.Atoi(playerId)
	return id
}

type UserCache struct {
	Cache *ttlcache.Cache[string, discordgo.User]
	Dg    *discordgo.Session
}

func NewUserCache(dg *discordgo.Session) UserCache {
	return UserCache{
		Cache: ttlcache.New[string, discordgo.User](),
		Dg:    dg,
	}
}

func (uc UserCache) FetchUsername(ctx context.Context, playerId string) (string, error) {
	user, err := uc.FetchUser(ctx, playerId)
	if err != nil || user == nil {
		return "", err
	}
	return user.Username, nil
}

func (uc UserCache) FetchPlayer(ctx context.Context, playerId string) (Player, error) {
	user, err := uc.FetchUser(ctx, playerId)
	if err != nil || user == nil {
		return Player{}, err
	}
	return PlayerFromUser(user), nil
}

var UserCacheTTl = time.Hour

func (uc UserCache) FetchUser(ctx context.Context, playerId string) (*discordgo.User, error) {
	trace := ctx.Value("trace")

	var user *discordgo.User
	var err error

	item := uc.Cache.Get(playerId)
	if item != nil {
		u := item.Value()
		user = &u
	} else {
		user, err = uc.Dg.User(playerId, discordgo.WithContext(ctx))
		if err != nil {
			slog.Error("failed to fetch user from discord", "trace", trace, "player", playerId, "error", err)
			return nil, err
		}
		uc.Cache.Set(playerId, *user, UserCacheTTl)
		slog.Info("set user back into the cache", "trace", trace, "user", user.Username, "player", playerId)
	}

	slog.Info("fetched user", "trace", trace, "user", user.Username, "player", playerId)
	return user, nil
}
