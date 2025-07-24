package othello

import (
	"math/rand"
	"time"
)

type Node struct {
	Set       bool
	Key       uint64
	Board     Board
	Heuristic float64
	Depth     int
}

type TTable struct {
	table  [][3]int
	cache  [][2]Node
	hits   int
	misses int
}

func NewTTable(cacheSize int, getRand func() int) TTable {
	cache := make([][2]Node, cacheSize)

	tableLen := BoardSize * BoardSize
	table := make([][3]int, tableLen)

	for i := 0; i < tableLen; i++ {
		for j := 0; j < 3; j++ {
			n := getRand()
			table[i][j] = n
		}
	}
	return TTable{table: table, cache: cache}
}

func NewTTableWithRand(cacheSize int) TTable {
	r := rand.New(rand.NewSource(time.Now().UnixNano()))
	getRand := func() int {
		return r.Intn(cacheSize)
	}
	return NewTTable(cacheSize, getRand)
}

func (tt *TTable) Hash(board Board) uint64 {
	var hash uint64 = 0
	for i := 0; i < len(tt.table); i++ {
		square := board.GetSquareByPosition(i)
		hash ^= uint64(tt.table[i][square])
	}
	return hash
}

func (tt *TTable) Clear() {
	tt.hits = 0
	tt.misses = 0
	for i := range tt.cache {
		tt.cache[i][0] = Node{}
		tt.cache[i][1] = Node{}
	}
}

func (tt *TTable) Put(key uint64, board Board, heuristic float64, depth int) {
	node := Node{
		Key:       key,
		Board:     board,
		Heuristic: heuristic,
		Depth:     depth,
		Set:       true,
	}

	h := int(node.Key % uint64(len(tt.cache)))
	cacheLine := &tt.cache[h]

	if cacheLine[0].Set {
		if node.Depth > cacheLine[0].Depth {
			cacheLine[1] = cacheLine[0]
			cacheLine[0] = node
		} else {
			cacheLine[1] = node
		}
	} else {
		cacheLine[0] = node
	}
}

func (tt *TTable) Get(key uint64, board Board) (Node, bool) {
	h := int(key % uint64(len(tt.cache)))

	cacheLine := &tt.cache[h]
	for _, n := range cacheLine {
		if n.Set && n.Board == board {
			tt.hits++
			return n, true
		}
	}
	tt.misses++
	return Node{}, false
}

func (tt *TTable) Hits() int {
	return tt.hits
}

func (tt *TTable) Misses() int {
	return tt.misses
}
