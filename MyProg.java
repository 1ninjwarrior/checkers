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

    // Evaluation weights
    private static final int PIECE_VALUE = 100;
    private static final int KING_VALUE = 175;
    private static final int POSITION_VALUE = 15;
    private static final int MOBILITY_VALUE = 10;
    private static final int JUMP_VALUE = 50;

    // Alpha-beta search with iterative deepening
    void FindBestMove(int player) {
        State state = new State();
        state.player = player;
        memcpy(state.board, board);
        memset(bestmove, 0, 12);

        FindLegalMoves(state);
        
        // If only one move is available, make it immediately
        if (state.moveptr == 1) {
            memcpy(bestmove, state.movelist[0], MoveLength(state.movelist[0]));
            return;
        }
        
        long startTime = System.currentTimeMillis();
        double timeLimit = SecPerMove * 900; // Use 90% of available time
        
        int depth = 1;
        int bestScore = Integer.MIN_VALUE;
        char[] currentBestMove = new char[12];
        
        // Always store the first valid move as fallback
        memcpy(currentBestMove, state.movelist[0], MoveLength(state.movelist[0]));
        
        try {
            while (System.currentTimeMillis() - startTime < timeLimit && depth <= MaxDepth) {
                boolean completedDepth = true;
                int localBestScore = Integer.MIN_VALUE;
                char[] localBestMove = new char[12];
                
                for (int i = 0; i < state.moveptr; i++) {
                    if (System.currentTimeMillis() - startTime >= timeLimit) {
                        completedDepth = false;
                        break;
                    }
                    
                    State nextState = new State();
                    nextState.player = 3 - player;
                    memcpy(nextState.board, state.board);
                    
                    char[] move = state.movelist[i];
                    PerformMove(nextState.board, move, MoveLength(move));
                    
                    int score = alphaBeta(nextState, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false, startTime, timeLimit);
                    
                    if (score > localBestScore) {
                        localBestScore = score;
                        memcpy(localBestMove, move, MoveLength(move));
                    }
                }
                
                if (completedDepth) {
                    bestScore = localBestScore;
                    memcpy(currentBestMove, localBestMove, MoveLength(localBestMove));
                    depth++;
                } else {
                    break;
                }
            }
        } finally {
            // Always ensure we have a move to make
            memcpy(bestmove, currentBestMove, MoveLength(currentBestMove));
        }
    }

    private int alphaBeta(State state, int depth, int alpha, int beta, boolean maximizing, 
                         long startTime, double timeLimit) {
        // Check if we're out of time
        if (System.currentTimeMillis() - startTime >= timeLimit) {
            throw new RuntimeException("Time limit exceeded");
        }

        // Base cases
        if (depth == 0) {
            return evaluatePosition(state);
        }
        
        FindLegalMoves(state);
        if (state.moveptr == 0) {
            return maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        if (maximizing) {
            int value = Integer.MIN_VALUE;
            for (int i = 0; i < state.moveptr; i++) {
                State nextState = new State();
                nextState.player = 3 - state.player;
                memcpy(nextState.board, state.board);
                
                char[] move = state.movelist[i];
                PerformMove(nextState.board, move, MoveLength(move));
                
                value = Math.max(value, alphaBeta(nextState, depth - 1, alpha, beta, false, startTime, timeLimit));
                alpha = Math.max(alpha, value);
                if (alpha >= beta) {
                    break;
                }
            }
            return value;
        } else {
            int value = Integer.MAX_VALUE;
            for (int i = 0; i < state.moveptr; i++) {
                State nextState = new State();
                nextState.player = 3 - state.player;
                memcpy(nextState.board, state.board);
                
                char[] move = state.movelist[i];
                PerformMove(nextState.board, move, MoveLength(move));
                
                value = Math.min(value, alphaBeta(nextState, depth - 1, alpha, beta, true, startTime, timeLimit));
                beta = Math.min(beta, value);
                if (alpha >= beta) {
                    break;
                }
            }
            return value;
        }
    }

    private int evaluatePosition(State state) {
        int score = 0;
        
        // Count material and position value
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (x % 2 != y % 2) {
                    char piece = state.board[y][x];
                    if (!empty(piece)) {
                        int pieceColor = color(piece);
                        int multiplier = (pieceColor == state.player) ? 1 : -1;
                        
                        // Material value
                        if (KING(piece)) {
                            score += KING_VALUE * multiplier;
                        } else {
                            score += PIECE_VALUE * multiplier;
                            
                            // Encourage forward movement for regular pieces
                            if (pieceColor == 1) { // Red pieces (moving down)
                                score += (y * 5) * multiplier; // More points for advancing
                            } else { // White pieces (moving up)
                                score += ((7 - y) * 5) * multiplier;
                            }
                        }
                        
                        // Position value - prefer center control and back row protection
                        int positionScore = 0;
                        // Center control bonus
                        if ((x == 2 || x == 3 || x == 4 || x == 5) && 
                            (y == 2 || y == 3 || y == 4 || y == 5)) {
                            positionScore += 3;
                        }
                        // Back row bonus
                        if ((pieceColor == 1 && y == 0) || (pieceColor == 2 && y == 7)) {
                            positionScore += 2;
                        }
                        // Edge control bonus
                        if (x == 0 || x == 7) {
                            positionScore += 1;
                        }
                        
                        score += POSITION_VALUE * positionScore * multiplier;
                    }
                }
            }
        }
        
        // Mobility value - count legal moves
        FindLegalMoves(state);
        score += state.moveptr * MOBILITY_VALUE;
        
        // Jump value - prefer positions with jump opportunities
        if (jumpptr > 0) {
            score += jumpptr * JUMP_VALUE;
        }
        
        return score;
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
    int TextToMove(String mtext, char[] move) {
        int i = 0, len = 0;
        String[] numbers = mtext.trim().split("-");
        
        try {
            for (String num : numbers) {
                int val = Integer.parseInt(num.trim());
                if (val <= 0 || val > 32) return 0;
                move[len++] = (char)val;
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing move: " + mtext);
            return 0;
        }
        
        return (len < 2 || len > 12) ? 0 : len;
    }

    /* Converts the integer array version of a move to its text version */
    String MoveToText(char move[])
    {
        int i;
        char temp[] = new char[8];

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
        int i,j,x,y,x1,y1,x2,y2;

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
        y2=xy[1];
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

    private String myRead(BufferedReader br, int expectedLen) {
        String rval = "";
        char line[] = new char[1000];
        int len = 0;
        System.err.println("Java waiting for input");
        
        try {
            len = br.read(line, 0, expectedLen > 0 ? expectedLen : 1000);
            if (len > 0) {
                rval = new String(line, 0, len).trim();
            }
        } catch(Exception e) { 
            System.err.println("Java read exception: " + e.getMessage()); 
        }
        
        System.err.println("Java read: '" + rval + "'");
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
            buf=myRead(br, 0);
            
            memset(move,0,12);

            /* Update the board to reflect opponents move */
            mlen = TextToMove(buf,move);
            PerformMove(board,move,mlen);
            
            /* Find my move, update board, and write move to pipe */
            if(player1!=0) FindBestMove(1); else FindBestMove(2);
            if(bestmove[0] != 0) { /* There is a legal move */
                mlen = MoveLength(bestmove);
                PerformMove(board,bestmove,mlen);
                buf = MoveToText(bestmove);
            }
            else System.exit(1); /* No legal moves available, so I have lost */

            /* Write the move to the pipe */
            System.err.println("Java move: " + buf);
            System.out.println(buf);
        }
    }
}

