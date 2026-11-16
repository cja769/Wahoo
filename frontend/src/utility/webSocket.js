import SockJS from "sockjs-client";
import Stomp from "stompjs";

export function initSocket(topic, callback) {
    const socket = new SockJS('/ws');
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, () => stompClient.subscribe(topic, callback), () => {
        setTimeout(() => {
            initSocket(topic, callback);
        }, 1000);
    });
    return stompClient;
};