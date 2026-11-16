import {act, render, screen} from "@testing-library/react";
import Board from "./Board";
import {initSocket} from "../utility/webSocket";

jest.mock("../utility/webSocket");

const states = ["p0", "p1", "p2", "p3"].map(playerId => ({
    playerId,
    playerName: playerId,
    homePositions: [],
    playerAreaPositions: [],
    safePositions: []
}));

function roll(currentRoll, overrides = {}) {
    return {
        states, gameId: "game", hasStarted: true, currentRoll,
        currentPlayerName: "Human", currentPlayerId: "p0", rollNumber: currentRoll,
        awaitingHumanMove: false, ...overrides
    };
}

test("shows five safe slots for each player and keeps the newest roll after a penalty", async () => {
    let receive;
    const close = jest.fn();
    initSocket.mockImplementation((topic, onMessage) => {
        receive = state => onMessage({body: JSON.stringify(state)});
        return close;
    });
    jest.spyOn(global, "fetch").mockResolvedValue({ok: true, json: async () => roll(1)});
    try {
        const {container, unmount} = render(
            <Board playerId="p0" gameId="game" playerToken="token" clearPlayerState={jest.fn()}/>
        );
        await screen.findByText("Human rolled 1");
        for (const {playerId} of states) {
            const lane = [...container.querySelectorAll(`[data-safe-player="${playerId}"]`)];
            expect(lane.map(slot => Number(slot.dataset.safeSlot)).sort()).toEqual([0, 1, 2, 3, 4]);
        }

        act(() => receive(roll(6, {rolledThreeSixes: true, penaltyApplied: false})));
        expect(screen.getByRole("status")).toHaveTextContent("no track marble to send home");
        act(() => receive(roll(4, {awaitingHumanMove: true})));
        expect(screen.getByRole("status")).toHaveTextContent("Human rolled 4 · Choose a marble");
        unmount();
        expect(close).toHaveBeenCalled();
    } finally {
        global.fetch.mockRestore();
    }
});
