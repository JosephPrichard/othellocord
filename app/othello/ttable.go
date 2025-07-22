package othello

import (
	"math/rand"
	"time"
)

type Node struct {
	Set       bool
	Key       uint64
	Heuristic float64
	Depth     int
}

type TTable struct {
	table  [][3]int
	cache  [][2]Node
	hits   int
	misses int
}

func NewTTable(cacheSize int) TTable {
	r := rand.New(rand.NewSource(time.Now().UnixNano()))

	cache := make([][2]Node, cacheSize)

	tableLen := BoardSize * BoardSize
	table := make([][3]int, tableLen)

	for i := 0; i < tableLen; i++ {
		for j := 0; j < 3; j++ {
			n := r.Int()
			if n < 0 {
				n = -n
			}
			table[i][j] = n
		}
	}

	return TTable{table: table, cache: cache}
}

func (t *TTable) Hash(board Board) uint64 {
	var hash uint64 = 0
	for i := 0; i < len(t.table); i++ {
		square := board.GetSquareByPosition(i)
		hash ^= uint64(t.table[i][square])
	}
	return hash
}

func (t *TTable) Clear() {
	t.hits = 0
	t.misses = 0
	for i := range t.cache {
		t.cache[i][0] = Node{}
		t.cache[i][1] = Node{}
	}
}

func (t *TTable) Put(node Node) {
	node.Set = true

	h := int(node.Key % uint64(len(t.cache)))
	cacheLine := t.cache[h]

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

func (t *TTable) Get(key uint64) (Node, bool) {
	h := int(key % uint64(len(t.cache)))
	cacheLine := t.cache[h]

	for _, n := range cacheLine {
		if n.Set && n.Key == key {
			t.hits++
			return n, true
		}
	}
	t.misses++
	return Node{}, false
}

func (t *TTable) Hits() int {
	return t.hits
}

func (t *TTable) Misses() int {
	return t.misses
}
