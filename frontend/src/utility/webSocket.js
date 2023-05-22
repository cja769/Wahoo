import SockJS from "sockjs-client";
import Stomp from "stompjs";

export function initSocket(topic, callback) {
    const socket = new SockJS('http://localhost:8080/ws');
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, () => stompClient.subscribe(topic, callback));
    return stompClient;
};