const ranges = {
    "p0" : {
        start : 51,
        end : 8,
        relativeToAbsoluteModifier : 0
    },
    "p1" : {
        start : 9,
        end : 22,
        relativeToAbsoluteModifier : 14
    },
    "p2" : {
        start : 23,
        end : 36,
        relativeToAbsoluteModifier : 28
    },
    "p3" : {
        start : 37,
        end : 50,
        relativeToAbsoluteModifier : 42
    }
}

const lastSpotOnBoard = 56;

export function getBoardPositions(boardState, player) {
    let boardPositions = [];
    const range = ranges[player];
    if (boardState.states) {
        for (let state of boardState.states) {
            for (let pos of state.playerAreaPositions) {
                let offSetPosition = pos.position + ranges[state.playerId].relativeToAbsoluteModifier;
                if ((offSetPosition >= range.start && offSetPosition <= range.end) ||
                    (range.start > range.end && offSetPosition >= range.start)) {
                    boardPositions.push({
                        ...pos,
                        position : offSetPosition - range.start,
                    })
                } else if (range.start > range.end && offSetPosition <= range.end) {
                    boardPositions.push({
                        ...pos,
                        position : lastSpotOnBoard - range.start + offSetPosition,
                    })
                }
            }
        }
    }
    return boardPositions;
}

export function getPlayerState(boardState, player) {
    return boardState?.states?.find(state => state.playerId === player);
}

export function getSpotState(boardState, positionToCheck) {
    if (!boardState) {
        return;
    }
    for (let pos of boardState) {
        if (pos.position === positionToCheck) {
            return pos;
        }
    }
    return;
}
