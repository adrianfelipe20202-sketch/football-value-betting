package com.valuebetting.repository;

import com.valuebetting.model.PaperBet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaperBetRepository extends JpaRepository<PaperBet, Long> {

    List<PaperBet> findByStatusOrderByPlacedAtDesc(PaperBet.Status status);

    List<PaperBet> findByMatchIdAndStatus(long matchId, PaperBet.Status status);

    boolean existsByMatchIdAndMarketAndSelectionAndStatus(long matchId, String market, String selection, PaperBet.Status status);

    List<PaperBet> findAllByOrderByPlacedAtDesc();

    @Query("SELECT COUNT(b) FROM PaperBet b WHERE b.status = 'WON'")
    long countWon();

    @Query("SELECT COUNT(b) FROM PaperBet b WHERE b.status = 'LOST'")
    long countLost();

    @Query("SELECT COUNT(b) FROM PaperBet b WHERE b.status = 'PENDING'")
    long countPending();

    @Query("SELECT COALESCE(SUM(b.profitLoss), 0) FROM PaperBet b WHERE b.status IN ('WON', 'LOST')")
    double totalProfitLoss();

    @Query("SELECT COALESCE(SUM(b.stakeAmount), 0) FROM PaperBet b WHERE b.status IN ('WON', 'LOST')")
    double totalStaked();

    // ROI split por minuto 80
    @Query("SELECT COALESCE(SUM(b.profitLoss), 0) FROM PaperBet b WHERE b.status IN ('WON', 'LOST') AND b.matchMinute < 80")
    double profitLossBefore80();

    @Query("SELECT COALESCE(SUM(b.stakeAmount), 0) FROM PaperBet b WHERE b.status IN ('WON', 'LOST') AND b.matchMinute < 80")
    double stakedBefore80();

    @Query("SELECT COUNT(b) FROM PaperBet b WHERE b.status IN ('WON', 'LOST') AND b.matchMinute < 80")
    long settledCountBefore80();

    @Query("SELECT COUNT(b) FROM PaperBet b WHERE b.status = 'WON' AND b.matchMinute < 80")
    long wonBefore80();

    @Query("SELECT COALESCE(SUM(b.profitLoss), 0) FROM PaperBet b WHERE b.status IN ('WON', 'LOST') AND b.matchMinute >= 80")
    double profitLossAfter80();

    @Query("SELECT COALESCE(SUM(b.stakeAmount), 0) FROM PaperBet b WHERE b.status IN ('WON', 'LOST') AND b.matchMinute >= 80")
    double stakedAfter80();

    @Query("SELECT COUNT(b) FROM PaperBet b WHERE b.status IN ('WON', 'LOST') AND b.matchMinute >= 80")
    long settledCountAfter80();

    @Query("SELECT COUNT(b) FROM PaperBet b WHERE b.status = 'WON' AND b.matchMinute >= 80")
    long wonAfter80();
}
