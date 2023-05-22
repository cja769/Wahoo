import SockJS from "sockjs-client";
import Stomp from "stompjs";
import getBackendUrl from "./constants";

export function initSocket(topic, callback) {
    const socket = new SockJS(getBackendUrl() + '/ws');
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, () => stompClient.subscribe(topic, callback));
    return stompClient;
};