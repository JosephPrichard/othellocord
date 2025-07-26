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

func PlayerFromUser(user *discordgo.User) Player {
	if user == nil {
		panic("expected user to be non nil when creating player")
	}
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

func NewUserCache(uf UserFetcher) UserCache {
	return UserCache{
		Cache: ttlcache.New[string, discordgo.User](),
		Uf:    uf,
	}
}

func (uc UserCache) GetUsername(ctx context.Context, playerId string) (string, error) {
	user, err := uc.GetUser(ctx, playerId)
	if err != nil || user == nil {
		return "", err
	}
	return user.Username, nil
}

func (uc UserCache) GetPlayer(ctx context.Context, playerId string) (Player, error) {
	user, err := uc.GetUser(ctx, playerId)
	if err != nil || user == nil {
		return Player{}, err
	}
	return PlayerFromUser(user), nil
}

var UserCacheTTl = time.Hour

func (uc UserCache) GetUser(ctx context.Context, playerId string) (*discordgo.User, error) {
	trace := ctx.Value("trace")

	var user *discordgo.User
	var err error

	item := uc.Cache.Get(playerId)
	if item != nil {
		u := item.Value()
		user = &u
	} else {
		if user, err = uc.Uf.User(playerId, discordgo.WithContext(ctx)); err != nil {
			slog.Error("failed to fetch user from discord", "trace", trace, "player", playerId, "err", err)
			return nil, err
		}
		uc.Cache.Set(playerId, *user, UserCacheTTl)
		slog.Info("set user back into the Cache", "trace", trace, "user", user.Username, "player", playerId)
	}

	slog.Info("fetched user", "trace", trace, "username", user.Username, "id", playerId)
	return user, nil
}
