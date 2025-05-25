/*
 * Copyright (c) Joseph Prichard 2024.
 */

package models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Entity
@Table(name = "Stats", indexes = {@Index(name = "idx_elo", columnList = "elo")})
@NoArgsConstructor
@AllArgsConstructor
public class StatsEntity {

    @Id
    private Long playerId;
    @Column(nullable = false)
    private Float elo;
    @Column(nullable = false)
    private Integer won;
    @Column(nullable = false)
    private Integer lost;
    @Column(nullable = false)
    private Integer drawn;

    public void incrementWon() {
        won++;
    }

    public void incrementLost() {
        lost++;
    }

    public StatsEntity(Long playerId) {
        this(playerId, 0f, 0, 0, 0);
    }
}
