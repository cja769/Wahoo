import SockJS from "sockjs-client";
import Stomp from "stompjs";

export function initSocket(topic, callback, onConnect) {
    let socket;
    let retry;
    let active = true;

    const connect = () => {
        socket = new SockJS("/ws");
        const client = Stomp.over(socket);
        client.debug = null;
        client.connect({}, () => {
            if (active) {
                client.subscribe(topic, callback);
                onConnect();
            }
        }, () => {
            if (active) retry = setTimeout(connect, 1000);
        });
    };
    connect();
    return () => {
        active = false;
        clearTimeout(retry);
        socket.close();
    };
}
