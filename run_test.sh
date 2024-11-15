#!/bin/bash

TOTAL_GAMES=25
player1_wins=0
player2_wins=0
draws=0

echo "Starting $TOTAL_GAMES games..."

for ((i=1; i<=$TOTAL_GAMES; i++)); do
    # Run checkers with output redirected to a temp file
    ./checkers ./computer "java MyProg" 1 > temp_output.txt 2>&1
    
    # Check the exit status to determine winner
    status=$?
    
    # Parse the output file for game result
    if grep -q "Player 2 has lost the game" temp_output.txt; then
        ((player1_wins++))
        echo "Game $i: Player 1 wins"
    elif grep -q "Player 1 has lost the game" temp_output.txt; then
        ((player2_wins++))
        echo "Game $i: Player 2 wins"
    else
        ((draws++))
        echo "Game $i: Draw"
    fi
    
    # Clean up temp file
    rm temp_output.txt
    
    # Print progress
    if ((i % 10 == 0)); then
        echo "Completed $i games..."
    fi
done

# Calculate percentages
p1_percent=$(echo "scale=2; ($player1_wins * 100) / $TOTAL_GAMES" | bc)
p2_percent=$(echo "scale=2; ($player2_wins * 100) / $TOTAL_GAMES" | bc)
draw_percent=$(echo "scale=2; ($draws * 100) / $TOTAL_GAMES" | bc)

# Print results
echo ""
echo "Results after $TOTAL_GAMES games:"
echo "--------------------------------"
echo "Player 1 wins: $player1_wins ($p1_percent%)"
echo "Player 2 wins: $player2_wins ($p2_percent%)"
echo "Draws: $draws ($draw_percent%)"