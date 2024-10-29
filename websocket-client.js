process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';

const WebSocket = require('ws');

const url = 'wss://localhost:8080/aws/athena/v1/stream';
// const url = 'wss://localhost:8080/bank/athena/v1/stream';
const connection = new WebSocket(url);

connection.onopen = () => {
  console.log('WebSocket connection established');

  // xxx is encode base64 token
  connection.send('d|a|||xxx');

  // Send a message every 30 seconds to keep the connection alive
  setInterval(() => {
  //  const message = JSON.stringify({ type: 'ping', data: 'keep-alive' });
    message = 'd|r|co||MSH,QNS,DGC,CAP,RAL,VFG,TLG,VCS,FOX,FPT';
    connection.send(message);
    console.log('Sent:', message);
  }, 1000);
};

connection.onerror = (error) => {
  console.error(`WebSocket error: ${error.message}`);
};

connection.onmessage = (message) => {
  console.log('Received:', message.data);
};

connection.onclose = (event) => {
  console.log(`WebSocket connection closed: ${event.code} - ${event.reason}`);
};
