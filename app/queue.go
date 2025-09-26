package app

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"log/slog"
	"time"
)

func PushQueue(ctx context.Context, q Query, gameID string, channelID string, time time.Time) error {
	_, err := q.ExecContext(ctx, "INSERT INTO bot_tasks(game_id, channel_id, push_time) VALUES ($1, $2, $3)", gameID, channelID, time)
	return err
}

func PushQueueNow(ctx context.Context, q Query, gameID string, channelID string) error {
	return PushQueue(ctx, q, gameID, channelID, time.Now())
}

func AckQueue(ctx context.Context, db *sql.DB, gameID string) error {
	_, err := db.ExecContext(ctx, "DELETE FROM bot_tasks WHERE game_id = ?", gameID)
	return err
}

type BotTask struct {
	channelID string
	game      OthelloGame
}

func scanTaskList(rows *sql.Rows) ([]BotTask, error) {
	var taskList []BotTask

	for rows.Next() {
		var task BotTask
		var row GameRow

		if err := rows.Scan(&task.channelID, &row.ID, &row.boardStr, &row.moveListStr, &row.whiteID, &row.blackID, &row.whiteName, &row.blackName); err != nil {
			return nil, fmt.Errorf("failed to scan bot task: %v", err)
		}
		game, err := mapGameRow(row)
		if err != nil {
			return nil, fmt.Errorf("failed to map game row: %v", err)
		}
		task.game = game

		taskList = append(taskList, task)
	}

	return taskList, nil
}

// SelectQueue select all elements from the queue
// in normal cases, the channel is used to read elements on the bot queue, but if the system fails in the middle of a bot task, this is used to recover unfinished tasks
func SelectQueue(ctx context.Context, db *sql.DB) ([]BotTask, error) {
	trace := ctx.Value(TraceKey)
	fail := func(err error) ([]BotTask, error) {
		slog.Error("failed to select tasks from queue", "trace", trace, "err", err)
		return nil, err
	}

	rows, err := db.QueryContext(ctx, `
		SELECT q.channel_id, q.depth, q.game_id, g.board, g.moves, g.white_id, g.black_id, g.white_name, g.black_name FROM bot_tasks q
		INNER JOIN games g ON game_id = id
		ORDER BY push_time`)
	if err != nil {
		return fail(err)
	}
	defer rows.Close()

	tasks, err := scanTaskList(rows)
	if err != nil {
		return fail(err)
	}
	slog.Info("selected tasks from queue", "trace", trace, "tasks", tasks)
	return tasks, nil
}

func PollQueue(db *sql.DB, state *State) chan BotTask {
	// this function contains a lot of "panics" because it makes a lot of assumptions about the behavior of the system

	taskCh := make(chan BotTask)

	handleTask := func(task BotTask) {
		ctx, cancel := context.WithTimeout(context.WithValue(context.Background(), TraceKey, "bot-queue-handle"), time.Second*10)
		defer cancel()

		depth := task.game.CurrentPlayer().LevelToDepth()
		if depth > 0 {
			// any game on the queue should be the bot's turn, but we check this in case the queue contains invalid data.
			// anyway, we always want to delete a task that is handled, but we can only handle a valid depth

			respCh := state.Sh.FindBestMove(task.game, depth)
			var resp MoveResp

			select {
			case resp = <-respCh:
			case <-ctx.Done():
				// the shell must respond in time, or we can assume there is a bug in the parser
				log.Fatalf("shell timed out while polling, this is likely a deadlock: %v", ctx.Err())
			}

			go HandleBotMove(ctx, state, task, resp.Moves)
		} else {
			slog.Info("handled a task with an invalid depth", "game", task.game.MarshalGGF(), "depth", depth)
		}

		// we can assume that if the task is received on the channel, it must already be persisted (this is an optimization to avoid redundant reads
		if err := AckQueue(ctx, db, task.game.ID); err != nil {
			slog.Error("failed to ack game in queue", "err", err)
		}
	}

	recoverTasks := func() {
		ctx := context.WithValue(context.Background(), TraceKey, "bot-queue-recover")

		// if there are any unfinished bot tasks (the bot failed before it could finish the tasks) they will be recovered here
		taskList, err := SelectQueue(ctx, db)
		if err != nil {
			// we should not fail to recover tasks from the queue on startup
			log.Fatalf("failed to select bot queue tasks, this is irrecoverable: %v", err)
		}
		slog.Info("recovered tasks on startup", "tasks", taskList)

		for _, task := range taskList {
			taskCh <- task
		}
	}

	pollTasks := func() {
		for task := range taskCh {
			handleTask(task)
		}
		// the bot queue can never be closed or games will get stuck on the bot's turn
		log.Fatal("finished polling all tasks, this should never happen")
	}

	go pollTasks()
	go recoverTasks()

	return taskCh
}
