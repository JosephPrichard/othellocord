package bot

import (
	"context"
	"fmt"
	"log/slog"
	"strconv"
	"time"

	"github.com/bwmarrin/discordgo"
	"github.com/jellydator/ttlcache/v3"
)

const MinBotLevel = 1
const MaxBotLevel = 5

type Player struct {
	ID   string
	Name string
}

func LevelToDepth(level int) int {
	switch level {
	case 1:
		return 3
	case 2:
		return 5
	case 3:
		return 6
	case 4:
		return 7
	case 5:
		return 8
	}
	return 0
}

func PlayerFromUser(user discordgo.User) Player {
	return Player{ID: user.ID, Name: user.Username}
}

func PlayerFromLevel(level int) Player {
	return Player{ID: strconv.Itoa(level), Name: GetBotLevelFmt(level)}
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
	id, err := strconv.Atoi(playerId)
	if err != nil {
		panic(fmt.Sprintf("expected bot player id to be a number, got %#v", playerId))
	}
	return id
}

type UserFetcher interface {
	User(userID string, options ...discordgo.RequestOption) (st *discordgo.User, err error)
}

type UserCacheApi interface {
	GetUsername(ctx context.Context, playerId string) (string, error)
	GetPlayer(ctx context.Context, playerId string) (Player, error)
}

type UserCache struct {
	Cache *ttlcache.Cache[string, discordgo.User]
	Uf    UserFetcher
}

func MakeUserCache(uf UserFetcher) UserCache {
	return UserCache{
		Cache: ttlcache.New[string, discordgo.User](),
		Uf:    uf,
	}
}

func (uc UserCache) GetUsername(ctx context.Context, playerId string) (string, error) {
	user, err := uc.GetUser(ctx, playerId)
	if err != nil {
		return "", err
	}
	return user.Username, nil
}

func (uc UserCache) GetPlayer(ctx context.Context, playerId string) (Player, error) {
	user, err := uc.GetUser(ctx, playerId)
	if err != nil {
		return Player{}, err
	}
	return PlayerFromUser(user), nil
}

const UserCacheTTl = time.Hour

func (uc UserCache) GetUser(ctx context.Context, playerId string) (discordgo.User, error) {
	trace := ctx.Value(TraceKey)

	var user discordgo.User

	item := uc.Cache.Get(playerId)
	if item != nil {
		user = item.Value()
	} else {
		u, err := uc.Uf.User(playerId, discordgo.WithContext(ctx))
		if err != nil {
			slog.Error("failed to fetch user from discord", "trace", trace, "player", playerId, "err", err)
			return discordgo.User{}, err
		}
		user = *u
		uc.Cache.Set(playerId, user, UserCacheTTl)
		slog.Info("set user back into the Cache", "trace", trace, "user", user.Username, "player", playerId)
	}

	slog.Info("fetched user", "trace", trace, "username", user.Username, "id", playerId)
	return user, nil
}
