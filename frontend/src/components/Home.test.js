import {act, fireEvent, render, screen} from "@testing-library/react";
import Home from "./Home";
import {initSocket} from "../utility/webSocket";

jest.mock("../utility/webSocket");

test("creates a comparison game using the single Create Game button", async () => {
    initSocket.mockReturnValue(() => {});
    const fetch = jest.spyOn(global, "fetch").mockImplementation(async path => {
        const body = path === "/api/bots" ? {ppoDefault: true, ppoPreview: true} : {games: []};
        return {ok: true, json: async () => body};
    });
    try {
        render(<Home setPlayerToken={jest.fn()}/>);
        await screen.findByRole("option", {name: "Earlier PPO checkpoint"});

        fireEvent.change(screen.getByLabelText("AI model"), {target: {value: "earlier"}});
        await act(async () => fireEvent.click(screen.getByRole("button", {name: "Create Game"})));

        expect(fetch).toHaveBeenCalledWith("/api/create?ppoPreview=true");
        expect(screen.getByRole("button", {name: "Create Game"})).toBeEnabled();
    } finally {
        fetch.mockRestore();
    }
});
