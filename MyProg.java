import java.util.*;
import java.io.*;


class State 
{
    int player;
    char board[][] = new char[8][8];
    char movelist[][] = new char[48][12]; /* The following comments were added by Tim Andersen
                  Here Scott has set the maximum number of possible legal moves to be 48.
                                  Be forewarned that this number might not be correct, even though I'm
                                  pretty sure it is.  
                  The second array subscript is (supposed to be) the maximum number of 
                  squares that a piece could visit in a single move.  This number is arrived
                  at be recognizing that an opponent will have at most 12 pieces on the 
                  board, and so you can at most jump twelve times.  However, the number
                  really ought to be 13 since we also have to record the starting position
                  as part of the move sequence.  I didn't code this, and I highly doubt 
                  that the natural course of a game would lead to a person jumping all twelve
                  of an opponents checkers in a single move, so I'm not going to change this. 
                  I'll leave it to the adventuresome to try and devise a way to legally generate
                  a board position that would allow such an event.  
                  Each move is represented by a sequence of numbers, the first number being 
                  the starting position of the piece to be moved, and the rest being the squares 
                  that the piece will visit (in order) during the course of a single move.  The 
                  last number in this sequence is the final position of the piece being moved.  */
    int moveptr;
}


public class MyProg
{
    public static final int Clear = 0x1f;
    public static final int Empty= 0x00;
    public static final int Piece= 0x20;
    public static final int King= 0x60;
    public static final int Red= 0x00;
    public static final int White= 0x80;
 
    float SecPerMove;
    char[][] board = new char[8][8];
    char[] bestmove = new char[12];
    int me,cutoff,endgame;
    long NumNodes;
    int MaxDepth;

    /*** For the jump list ***/
    int jumpptr = 0;
    int jumplist[][] = new int[48][12];

    /*** For the move list ***/
    int moveptr = 0;
    int movelist[][] = new int[48][12];

    Random random = new Random();

    private long startTime;
    private boolean timeIsUp;

    private static final int PIECE_VALUE = 100;
    private static final int KING_VALUE = 200;
    private static final int BACK_ROW_BONUS = 30;
    private static final int MIDDLE_BOX_BONUS = 35;
    private static final int PROTECTED_PIECE_BONUS = 20;
    private static final int MOBILITY_BONUS = 15;
    private static final int AGGRESSIVE_BONUS = 25;
    private static final int ENDGAME_KING_BONUS = 50;
    private static final int PROMOTION_PATH_BONUS = 20;

    public int number(char x) { return ((x)&0x1f); }
    public boolean empty(char x) { return ((((x)>>5)&0x03)==0?1:0) != 0; }
    public boolean piece(char x) { return ((((x)>>5)&0x03)==1?1:0) != 0; }
    public boolean KING(char x) { return ((((x)>>5)&0x03)==3?1:0) != 0; }
    public int color(char x) { return ((((x)>>7)&1)+1); }

    public void memcpy(char[][] dest, char[][] src)
    {
        for(int x=0;x<8;x++) for(int y=0;y<8;y++) dest[x][y]=src[x][y];
    }

    public void memcpy(char[] dest, char[] src, int num)
    {
        for(int x=0;x<num;x++) dest[x]=src[x];
    }

    public void memset(char[] arr, int val, int num)
    {
        for(int x=0;x<num;x++) arr[x]=(char)val;
    }

    /* Copy a square state */
    char CopyState(char dest, char src)
    {
        char state;
        
        dest &= Clear;
        state = (char)(src & 0xE0);
        dest |= state;
        return dest;
    }

    /* Reset board to initial configuration */
    void ResetBoard()
    {
        int x,y;
        char pos;

        pos = 0;
        for(y=0; y<8; y++)
        for(x=0; x<8; x++)
        {
            if(x%2 != y%2) {
                board[y][x] = pos;
                if(y<3 || y>4) board[y][x] |= Piece; else board[y][x] |= Empty;
                if(y<3) board[y][x] |= Red; 
                if(y>4) board[y][x] |= White;
                pos++;
            } else board[y][x] = 0;
        }
        endgame = 0;
    }

    /* Add a move to the legal move list */
    void AddMove(char move[])
    {
        int i;

        for(i=0; i<12; i++) movelist[moveptr][i] = move[i];
        moveptr++;
    }

    /* Finds legal non-jump moves for the King at position x,y */
    void FindKingMoves(char board[][], int x, int y) 
    {
        int i,j,x1,y1;
        char move[] = new char[12];

        memset(move,0,12);

        /* Check the four adjacent squares */
        for(j=-1; j<2; j+=2)
        for(i=-1; i<2; i+=2)
        {
            y1 = y+j; x1 = x+i;
            /* Make sure we're not off the edge of the board */
            if(y1<0 || y1>7 || x1<0 || x1>7) continue; 
            if(empty(board[y1][x1])) {  /* The square is empty, so we can move there */
                move[0] = (char)(number(board[y][x])+1);
                move[1] = (char)(number(board[y1][x1])+1);    
                AddMove(move);
            }
        }
    }

    /* Finds legal non-jump moves for the Piece at position x,y */
    void FindMoves(int player, char board[][], int x, int y) 
    {
        int i,j,x1,y1;
        char move[] = new char[12];

        memset(move,0,12);

        /* Check the two adjacent squares in the forward direction */
        if(player == 1) j = 1; else j = -1;
        for(i=-1; i<2; i+=2)
        {
            y1 = y+j; x1 = x+i;
            /* Make sure we're not off the edge of the board */
            if(y1<0 || y1>7 || x1<0 || x1>7) continue; 
            if(empty(board[y1][x1])) {  /* The square is empty, so we can move there */
                move[0] = (char)(number(board[y][x])+1);
                move[1] = (char)(number(board[y1][x1])+1);    
                AddMove(move);
            }
        }
    }

    /* Adds a jump sequence the the legal jump list */
    void AddJump(char move[])
    {
        int i;
        
        for(i=0; i<12; i++) jumplist[jumpptr][i] = move[i];
        jumpptr++;
    }

    /* Finds legal jump sequences for the King at position x,y */
    int FindKingJump(int player, char board[][], char move[], int len, int x, int y) 
    {
        int i,j,x1,y1,x2,y2,FoundJump = 0;
        char one,two;
        char mymove[] = new char[12];
        char myboard[][] = new char[8][8];

        memcpy(mymove,move,12);

        /* Check the four adjacent squares */
        for(j=-1; j<2; j+=2)
        for(i=-1; i<2; i+=2)
        {
            y1 = y+j; x1 = x+i;
            y2 = y+2*j; x2 = x+2*i;
            /* Make sure we're not off the edge of the board */
            if(y2<0 || y2>7 || x2<0 || x2>7) continue; 
            one = board[y1][x1];
            two = board[y2][x2];
            /* If there's an enemy piece adjacent, and an empty square after hum, we can jump */
            if(!empty(one) && color(one) != player && empty(two)) {
                /* Update the state of the board, and recurse */
                memcpy(myboard,board);
                myboard[y][x] &= Clear;
                myboard[y1][x1] &= Clear;
                mymove[len] = (char)(number(board[y2][x2])+1);
                FoundJump = FindKingJump(player,myboard,mymove,len+1,x+2*i,y+2*j);
                if(FoundJump==0) {
                    FoundJump = 1;
                    AddJump(mymove);
                }
            }
        }
        return FoundJump;
    }

    /* Finds legal jump sequences for the Piece at position x,y */
    int FindJump(int player, char board[][], char move[], int len, int x, int y) 
    {
        int i,j,x1,y1,x2,y2,FoundJump = 0;
        char one,two;
        char mymove[] = new char[12];
        char myboard[][] = new char[8][8];

        memcpy(mymove,move,12);

        /* Check the two adjacent squares in the forward direction */
        if(player == 1) j = 1; else j = -1;
        for(i=-1; i<2; i+=2)
        {
            y1 = y+j; x1 = x+i;
            y2 = y+2*j; x2 = x+2*i;
            /* Make sure we're not off the edge of the board */
            if(y2<0 || y2>7 || x2<0 || x2>7) continue; 
            one = board[y1][x1];
            two = board[y2][x2];
            /* If there's an enemy piece adjacent, and an empty square after him, we can jump */
            if(!empty(one) && color(one) != player && empty(two)) {
                /* Update the state of the board, and recurse */
                memcpy(myboard,board);
                myboard[y][x] &= Clear;
                myboard[y1][x1] &= Clear;
                mymove[len] = (char)(number(board[y2][x2])+1);
                FoundJump = FindJump(player,myboard,mymove,len+1,x+2*i,y+2*j);
                if(FoundJump==0) {
                    FoundJump = 1;
                    AddJump(mymove);
                }
            }
        }
        return FoundJump;
    }

    /* Determines all of the legal moves possible for a given state */
    int FindLegalMoves(State state)
    {
        int x,y;
        char move[] = new char[12], board[][] = new char[8][8];

        memset(move,0,12);
        jumpptr = moveptr = 0;
        memcpy(board,state.board);

        /* Loop through the board array, determining legal moves/jumps for each piece */
        for(y=0; y<8; y++)
        for(x=0; x<8; x++)
        {
            if(x%2 != y%2 && color(board[y][x]) == state.player && !empty(board[y][x])) {
                if(KING(board[y][x])) { /* King */
                    move[0] = (char)(number(board[y][x])+1);
                    FindKingJump(state.player,board,move,1,x,y);
                    if(jumpptr==0) FindKingMoves(board,x,y);
                } 
                else if(piece(board[y][x])) { /* Piece */
                    move[0] = (char)(number(board[y][x])+1);
                    FindJump(state.player,board,move,1,x,y);
                    if(jumpptr==0) FindMoves(state.player,board,x,y);    
                }
            }    
        }
        if(jumpptr!=0) {
            for(x=0; x<jumpptr; x++) 
            for(y=0; y<12; y++) 
            state.movelist[x][y] = (char)(jumplist[x][y]);
            state.moveptr = jumpptr;
        } 
        else {
            for(x=0; x<moveptr; x++) 
            for(y=0; y<12; y++) 
            state.movelist[x][y] = (char)(movelist[x][y]);
            state.moveptr = moveptr;
        }
        return (jumpptr+moveptr);
    }

    private int EvaluatePosition(char[][] board, int player) {
        int score = 0;
        int opponent = (player == 1) ? 2 : 1;
        int myPieces = 0;
        int opponentPieces = 0;
        
        for(int y = 0; y < 8; y++) {
            for(int x = 0; x < 8; x++) {
                if(x%2 != y%2 && !empty(board[y][x])) {
                    if(color(board[y][x]) == player) {
                        myPieces++;
                        score += evaluatePiece(board, x, y, player, true);
                    } else {
                        opponentPieces++;
                        score -= evaluatePiece(board, x, y, opponent, false);
                    }
                }
            }
        }

        boolean isEndgame = (myPieces + opponentPieces) <= 8;
        if(isEndgame) {
            score += evaluateEndgame(board, player, myPieces, opponentPieces);
        }

        score += evaluateMobility(board, player) * MOBILITY_BONUS;
        
        return score;
    }

    private int evaluatePiece(char[][] board, int x, int y, int player, boolean mine) {
        int score = 0;
        int multiplier = mine ? 1 : 1;

        score += PIECE_VALUE;

        if(KING(board[y][x])) {
            score += KING_VALUE;
            if((x >= 2 && x <= 5) && (y >= 2 && y <= 5)) {
                score += 15;
            }
        } else {
            int promotionDistance = (player == 1) ? (7 - y) : y;
            score += (PROMOTION_PATH_BONUS * (7 - promotionDistance)) / 7;
        }

        if((player == 1 && y == 7) || (player == 2 && y == 0)) {
            score += BACK_ROW_BONUS;
        }

        if((y == 3 || y == 4) && (x >= 2 && x <= 5)) {
            score += MIDDLE_BOX_BONUS;
        }

        if(IsProtected(board, x, y, player)) {
            score += PROTECTED_PIECE_BONUS;
        }

        if(hasAttackingPosition(board, x, y, player)) {
            score += AGGRESSIVE_BONUS;
        }
        
        return score * multiplier;
    }

    private int evaluateEndgame(char[][] board, int player, int myPieces, int opponentPieces) {
        int score = 0;
        int opponent = (player == 1) ? 2 : 1;

        score += (myPieces - opponentPieces) * 200;
        
        int myKingCount = 0;
        int opponentKingCount = 0;
        int distanceToOpponents = 0;
        
        for(int y = 0; y < 8; y++) {
            for(int x = 0; x < 8; x++) {
                if(x%2 != y%2 && !empty(board[y][x])) {
                    if(color(board[y][x]) == player) {
                        if(KING(board[y][x])) {
                            myKingCount++;
                            distanceToOpponents -= getMinDistanceToOpponents(board, x, y, opponent);
                        }
                        if(player == 1) {
                            score += (y * 30);
                        } else {
                            score += ((7-y) * 30);
                        }
                    } else if(KING(board[y][x])) {
                        opponentKingCount++;
                    }
                }
            }
        }
        
        score += (myKingCount - opponentKingCount) * ENDGAME_KING_BONUS * 2;
        
        score += distanceToOpponents * 25;
        
        if(myPieces > opponentPieces) {
            score += getAggressiveScore(board, player) * 40;
        }
        
        if(myKingCount > 0 && opponentKingCount == 0) {
            score += forceWinStrategy(board, player, opponent) * 100;
        }
        
        return score;
    }
    
    private int getMinDistanceToOpponents(char[][] board, int x, int y, int opponent) {
        int minDistance = 100;
        for(int i = 0; i < 8; i++) {
            for(int j = 0; j < 8; j++) {
                if(j%2 != i%2 && !empty(board[i][j]) && color(board[i][j]) == opponent) {
                    int distance = Math.abs(x - j) + Math.abs(y - i);
                    minDistance = Math.min(minDistance, distance);
                }
            }
        }
        return minDistance;
    }

    private int getAggressiveScore(char[][] board, int player) {
        int score = 0;
        for(int y = 0; y < 8; y++) {
            for(int x = 0; x < 8; x++) {
                if(x%2 != y%2 && !empty(board[y][x]) && color(board[y][x]) == player) {
                    if(hasAttackingPosition(board, x, y, player)) {
                        score += 50;
                    }
                }
            }
        }
        return score;
    }

    private int forceWinStrategy(char[][] board, int player, int opponent) {
        int score = 0;
        int opponentPieceX = -1;
        int opponentPieceY = -1;
        
        for(int y = 0; y < 8; y++) {
            for(int x = 0; x < 8; x++) {
                if(x%2 != y%2 && !empty(board[y][x]) && color(board[y][x]) == opponent) {
                    opponentPieceX = x;
                    opponentPieceY = y;
                    break;
                }
            }
        }
        
        if(opponentPieceX != -1) {
            int minKingDistance = 100;
            for(int y = 0; y < 8; y++) {
                for(int x = 0; x < 8; x++) {
                    if(x%2 != y%2 && !empty(board[y][x]) && 
                       color(board[y][x]) == player && KING(board[y][x])) {
                        int distance = Math.abs(x - opponentPieceX) + Math.abs(y - opponentPieceY);
                        minKingDistance = Math.min(minKingDistance, distance);
                    }
                }
            }
            score += (14 - minKingDistance) * 50;
            
            if(opponentPieceX == 0 || opponentPieceX == 7 || 
               opponentPieceY == 0 || opponentPieceY == 7) {
                score += 200;
            }
        }
        
        return score;
    }

    private int evaluateMobility(char[][] board, int player) {
        State tempState = new State();
        tempState.player = player;
        memcpy(tempState.board, board);
        
        FindLegalMoves(tempState);
        return tempState.moveptr;
    }

    private boolean hasAttackingPosition(char[][] board, int x, int y, int player) {
        int forward = (player == 1) ? 1 : -1;
        
        for(int dx = -2; dx <= 2; dx += 4) {
            int ny = y + (forward * 2);
            int nx = x + dx;
            
            if(ny >= 0 && ny < 8 && nx >= 0 && nx < 8) {
                if(!empty(board[y + forward][x + (dx/2)]) && 
                   color(board[y + forward][x + (dx/2)]) != player &&
                   empty(board[ny][nx])) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private int MinVal(State state, int alpha, int beta, int depth) {
        if (timeIsUp || isTimeUp()) {
            timeIsUp = true;
            return EvaluatePosition(state.board, me);
        }
        
        if (depth == 0) {
            int score = EvaluatePosition(state.board, me);
            return score;
        }
        
        FindLegalMoves(state);
        if(state.moveptr == 0) return Integer.MAX_VALUE;
        
        sortMoves(state);
        
        for(int i = 0; i < state.moveptr; i++) {
            State nextstate = new State();
            nextstate.player = (state.player == 1) ? 2 : 1;
            memcpy(nextstate.board, state.board);
            
            int mlen = MoveLength(state.movelist[i]);
            PerformMove(nextstate.board, state.movelist[i], mlen);
            
            int val = MaxVal(nextstate, alpha, beta, depth - 1);
            if(val < beta) beta = val;
            if(beta <= alpha) return alpha;
        }
        return beta;
    }

    private int MaxVal(State state, int alpha, int beta, int depth) {
        if (timeIsUp || isTimeUp()) {
            timeIsUp = true;
            return EvaluatePosition(state.board, me);
        }
        
        if (depth == 0) return EvaluatePosition(state.board, me);
        
        FindLegalMoves(state);
        if(state.moveptr == 0) return Integer.MIN_VALUE; 
        
        sortMoves(state);
        
        for(int i = 0; i < state.moveptr; i++) {
            State nextstate = new State();
            nextstate.player = (state.player == 1) ? 2 : 1;
            memcpy(nextstate.board, state.board);
            
            int mlen = MoveLength(state.movelist[i]);
            PerformMove(nextstate.board, state.movelist[i], mlen);
            
            int val = MinVal(nextstate, alpha, beta, depth - 1);
            if(val > alpha) alpha = val;
            if(alpha >= beta) return beta;
        }
        return alpha;
    }

    private void sortMoves(State state) {
        int[] moveScores = new int[state.moveptr];
        
        for(int i = 0; i < state.moveptr; i++) {
            State nextstate = new State();
            nextstate.player = (state.player == 1) ? 2 : 1;
            memcpy(nextstate.board, state.board);
            
            int mlen = MoveLength(state.movelist[i]);
            PerformMove(nextstate.board, state.movelist[i], mlen);
            
            moveScores[i] = EvaluatePosition(nextstate.board, me);
        }
        
        for(int i = 0; i < state.moveptr - 1; i++) {
            for(int j = 0; j < state.moveptr - i - 1; j++) {
                if(moveScores[j] < moveScores[j + 1]) {
                    char[] tempMove = new char[12];
                    memcpy(tempMove, state.movelist[j], 12);
                    memcpy(state.movelist[j], state.movelist[j + 1], 12);
                    memcpy(state.movelist[j + 1], tempMove, 12);
                    
                    int tempScore = moveScores[j];
                    moveScores[j] = moveScores[j + 1];
                    moveScores[j + 1] = tempScore;
                }
            }
        }
    }

    private boolean isValidMove(char[][] board, char[] move, int mlen) {
        if (mlen < 2 || mlen > 12) return false;
        
        int[] xy = new int[2];
        NumberToXY(move[0], xy);
        int startX = xy[0];
        int startY = xy[1];
        
        if (empty(board[startY][startX]) || color(board[startY][startX]) != me) {
            return false;
        }
        
        for (int i = 1; i < mlen; i++) {
            NumberToXY(move[i], xy);
            int endX = xy[0];
            int endY = xy[1];
            
            if (endX < 0 || endX > 7 || endY < 0 || endY > 7 || !empty(board[endY][endX])) {
                return false;
            }
            
            if (Math.abs(endX - startX) == 2) {
                int jumpedX = startX + (endX - startX) / 2;
                int jumpedY = startY + (endY - startY) / 2;
                
                if (empty(board[jumpedY][jumpedX]) || color(board[jumpedY][jumpedX]) == me) {
                    return false;
                }
            }
            else if (Math.abs(endX - startX) == 1) {
                if (!KING(board[startY][startX])) {
                    if ((me == 1 && endY <= startY) || (me == 2 && endY >= startY)) {
                        return false;
                    }
                }
            } else {
                return false;
            }
            
            startX = endX;
            startY = endY;
        }
        
        return true;
    }

    void FindBestMove(int player) {
        startTime = System.currentTimeMillis();
        timeIsUp = false;
        int currentDepth = 1;
        
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        int bestIndex = 0;
        char[] currentBestMove = new char[12];
        
        State state = new State();
        state.player = player;
        memcpy(state.board, board);
        memset(bestmove, 0, 12);
        
        FindLegalMoves(state);
        
        if (state.moveptr == 1) {
            memcpy(bestmove, state.movelist[0], MoveLength(state.movelist[0]));
            return;
        }

        while (!timeIsUp && (MaxDepth == -1 || currentDepth <= MaxDepth)) {
            boolean completedDepth = true;
            int tempBestIndex = 0;
            
            int[] moveScores = new int[state.moveptr];
            List<Integer> moveIndices = new ArrayList<>();
            
            for (int i = 0; i < state.moveptr && !timeIsUp; i++) {
                State nextstate = new State();
                nextstate.player = (player == 1) ? 2 : 1;
                memcpy(nextstate.board, state.board);
                
                int mlen = MoveLength(state.movelist[i]);
                PerformMove(nextstate.board, state.movelist[i], mlen);
                
                int value = MinVal(nextstate, alpha, beta, currentDepth - 1);
                moveScores[i] = value;
                moveIndices.add(i);
                
                if (isTimeUp()) {
                    timeIsUp = true;
                    completedDepth = false;
                    break;
                }
            }
            
            moveIndices.sort((a, b) -> Integer.compare(moveScores[b], moveScores[a]));
            tempBestIndex = moveIndices.get(0);
            
            if (completedDepth) {
                bestIndex = tempBestIndex;
                memcpy(currentBestMove, state.movelist[bestIndex], MoveLength(state.movelist[bestIndex]));
                memcpy(bestmove, currentBestMove, 12);
            }
            
            currentDepth++;
        }
        
        // Add move validation before performing the move
        if (bestmove[0] != 0) {
            int mlen = MoveLength(bestmove);
            if (!isValidMove(board, bestmove, mlen)) {
                // If best move is invalid, try to find any valid move
                for (int i = 0; i < state.moveptr; i++) {
                    if (isValidMove(board, state.movelist[i], MoveLength(state.movelist[i]))) {
                        memcpy(bestmove, state.movelist[i], 12);
                        break;
                    }
                }
                // If no valid moves found, set bestmove to 0
                if (!isValidMove(board, bestmove, MoveLength(bestmove))) {
                    memset(bestmove, 0, 12);
                }
            }
        }
    }

    private boolean isTimeUp() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        return elapsedTime >= (SecPerMove * 1000 - 50);
    }

    /* Converts a square label to it's x,y position */
    void NumberToXY(char num, int[] xy)
    {
        int i=0,newy,newx;

        for(newy=0; newy<8; newy++)
        for(newx=0; newx<8; newx++)
        {
            if(newx%2 != newy%2) {
                i++;
                if(i==(int) num) {
                    xy[0] = newx;
                    xy[1] = newy;
                    return;
                }
            }
        }
        xy[0] = 0; 
        xy[1] = 0;
    }

    /* Returns the length of a move */
    int MoveLength(char move[])
    {
        int i;

        i = 0;
        while(i<12 && move[i]!=0) i++;
        return i;
    }    

    /* Converts the text version of a move to its integer array version */
    int TextToMove(String mtext, char[] move)
    {
        int len=0,last;
        char val;
        String num;

        for (int i = 0; i < mtext.length() && mtext.charAt(i) != '\0';) 
        {
            last = i;
            while(i < mtext.length() && mtext.charAt(i) != '\0' && mtext.charAt(i) != '-') i++;

            num = mtext.substring(last,i);
            val = (char)Integer.parseInt(num);

            if(val <= 0 || val > 32) return 0;
            move[len] = val;
            len++;
            if(i < mtext.length() && mtext.charAt(i) != '\0') i++;
        }
        if(len<2 || len>12) return 0; else return len;
    }

    /* Converts the integer array version of a move to its text version */
    String MoveToText(char move[])
    {
        int i;

        String mtext = "";
        if(move[0]!=0) 
        {
           mtext += ((int)(move[0]));
           for(i=1; i<12; i++) {
               if(move[i]!=0) {
                   mtext += "-";
                   mtext += ((int)(move[i]));
               }
           }
        }
        return mtext;
    }

    /* Performs a move on the board, updating the state of the board */
    void PerformMove(char board[][], char move[], int mlen)
    {
        int i,j,x,y,x1,y1,x2;

        int xy[] = new int[2];

        NumberToXY(move[0],xy);
        x=xy[0];
        y=xy[1];
        NumberToXY(move[mlen-1],xy);
        x1=xy[0];
        y1=xy[1];
        board[y1][x1] = CopyState(board[y1][x1], board[y][x]);
        if(y1 == 0 || y1 == 7) board[y1][x1] |= King;
        board[y][x] &= Clear;
        NumberToXY(move[1],xy);
        x2=xy[0];
        if(Math.abs(x2-x) == 2) {
            for(i=0,j=1; j<mlen; i++,j++) {
                if(move[i] > move[j]) {
                    y1 = -1; 
                    if((move[i]-move[j]) == 9) x1 = -1; else x1 = 1;
                }
                else {
                    y1 = 1;
                    if((move[j]-move[i]) == 7) x1 = -1; else x1 = 1;
                }
                NumberToXY(move[i],xy);
                x=xy[0];
                y=xy[1];
                board[y+y1][x+x1] &= Clear;
            }
        }
    }

    public static void main(String argv[]) throws Exception
    {
        System.err.println("AAAAA");
        if(argv.length>=2) System.err.println("Argument:" + argv[1]);
        MyProg stupid = new MyProg();
        stupid.play(argv);
    }

    String myRead(BufferedReader br, int y)
    {
        String rval = "";
        char line[] = new char[1000];
        int x,len=0;
System.err.println("Java waiting for input");
        try
        {
           //while(!br.ready()) ;
           len = br.read(line, 0, y);
        }
        catch(Exception e) { System.err.println("Java wio exception"); }
        for(x=0;x<len;x++) rval += line[x];
System.err.println("Java read " + len + " chars: " + rval);
        return rval;
    }


    String myRead(BufferedReader br)
    {
        String rval = "";
        char line[] = new char[1000];
        int x,len=0;
        try
        {
           //while(!br.ready()) ;
           len = br.read(line, 0, 1000);
        }
        catch(Exception e) { System.err.println("Java wio exception"); }
        for(x=0;x<len;x++) rval += line[x];
        return rval;
    }

    public void play(String argv[]) throws Exception
    {
        char move[] = new char[12];
        int mlen,player1;
        String buf;

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        /* Convert command line parameters */
        SecPerMove = (float)(Double.parseDouble(argv[0]));
        MaxDepth = (argv.length == 2) ? Integer.parseInt(argv[1]) : -1;

        System.err.println("Java maximum search depth = " + MaxDepth);

        /* Determine if I am player 1 (red) or player 2 (white) */
        //buf = br.readLine();
        buf = myRead(br, 7);
        if(buf.startsWith("Player1")) 
        {
           System.err.println("Java is player 1");
           player1=1;
        }
        else 
        {
           System.err.println("Java is player 2");
           player1=0;
        }
        if(player1!=0) me = 1; else me = 2;

        /* Set up the board */ 
        ResetBoard();

        if (player1!=0) 
        {
            /* Find my move, update board, and write move to pipe */
            if(player1!=0) FindBestMove(1); else FindBestMove(2);
            if(bestmove[0] != 0) { /* There is a legal move */
                mlen = MoveLength(bestmove);
                PerformMove(board,bestmove,mlen);
                buf = MoveToText(bestmove);
            }
            else System.exit(1); /* No legal moves available, so I have lost */

            /* Write the move to the pipe */
            System.err.println("Java making first move: " + buf);
            System.out.println(buf);
        }

        for(;;) 
        {
            /* Read the other player's move from the pipe */
            //buf=br.readLine();
            buf=myRead(br);
            
            memset(move,0,12);

            /* Update the board to reflect opponents move */
            mlen = TextToMove(buf,move);
            PerformMove(board,move,mlen);
            
            /* Find my move, update board, and write move to pipe */
            if(player1!=0) FindBestMove(1); else FindBestMove(2);
            if(bestmove[0] != 0) { /* There is a legal move */
                mlen = MoveLength(bestmove);
                if (isValidMove(board, bestmove, mlen)) {
                    PerformMove(board, bestmove, mlen);
                    buf = MoveToText(bestmove);
                } else {
                    System.exit(1); // Exit if no valid move is found
                }
            } else {
                System.exit(1);
            }

            /* Write the move to the pipe */
            System.err.println("Java move: " + buf);
            System.out.println(buf);
        }
    }

    private boolean IsProtected(char[][] board, int x, int y, int player) {
        int forward = (player == 1) ? 1 : -1;
        int backward = -forward;
        
        for (int dx = -1; dx <= 1; dx += 2) {
            int backX = x + dx;
            int backY = y + backward;
            
            if (backX >= 0 && backX < 8 && backY >= 0 && backY < 8) {
                if (!empty(board[backY][backX]) && 
                    color(board[backY][backX]) == player) {
                    return true;
                }
            }
            
            if (KING(board[y][x])) {
                int frontX = x + dx;
                int frontY = y + forward;
                
                if (frontX >= 0 && frontX < 8 && frontY >= 0 && frontY < 8) {
                    if (!empty(board[frontY][frontX]) && 
                        color(board[frontY][frontX]) == player) {
                        return true;
                    }
                }
            }
        }
        
        if (x == 0 || x == 7) {
            return true;
        }
        
        return false;
    }
}

