CC              := gcc
CFLAGS          := -Wall -ggdb
CPPFLAGS        := -I./ -I/opt/X11/include -I/opt/homebrew/include -I/opt/homebrew/include/Xm
LDFLAGS         := -L/opt/X11/lib -L/opt/homebrew/lib -lXm -lXt -lX11

# Comment out this line to disable graphics
# CFLAGS          += -DGRAPHICS

all: checkers computer
checkers: graphics.o
computer: myprog.o
	${CC} ${CPPFLAGS} ${CFLAGS} -o $@ $^

.PHONY: clean
clean:	
	@-rm checkers computer *.o
