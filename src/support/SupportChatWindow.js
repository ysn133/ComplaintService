import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { formatDistanceToNow } from 'date-fns';
import { PhoneIcon } from '@heroicons/react/24/outline';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const SupportChatWindow = ({ ticket }) => {
  const [messages, setMessages] = useState([]);
  const [newMessage, setNewMessage] = useState('');
  const [ticketStatus, setTicketStatus] = useState(ticket?.status || 'Open');
  const [isStatusMenuOpen, setIsStatusMenuOpen] = useState(false);
  const [pendingStatus, setPendingStatus] = useState(ticket?.status || 'Open');
  const stompClientRef = useRef(null);
  const [subscription, setSubscription] = useState(null);
  const [callSubscription, setCallSubscription] = useState(null);
  const [incomingCall, setIncomingCall] = useState(null);
  const callIdRef = useRef(null);
  const [callStatus, setCallStatus] = useState(null);
  const [isMuted, setIsMuted] = useState(false);
  const [isOnHold, setIsOnHold] = useState(false);
  const [volume, setVolume] = useState(1.0);
  const [callDuration, setCallDuration] = useState(0);
  const [isRecording, setIsRecording] = useState(false);
  const [isScreenSharing, setIsScreenSharing] = useState(false);
  const messagesEndRef = useRef(null);
  const localStreamRef = useRef(null);
  const remoteStreamRef = useRef(null);
  const peerConnectionRef = useRef(null);
  const audioRef = useRef(null);
  const videoRef = useRef(null);
  const timerRef = useRef(null);
  const screenShareModalRef = useRef(null);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [size, setSize] = useState({ width: 640, height: 480 });
  const [dragging, setDragging] = useState(false);
  const [resizing, setResizing] = useState(false);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
  const [resizeStart, setResizeStart] = useState({ x: 0, y: 0 });

  const token = 'eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiU1VQUE9SVCIsInVzZXJJZCI6Miwic3ViIjoiMiIsImlhdCI6MTc0NDg0MzI0NCwiZXhwIjoxNzQ0OTI5NjQ0fQ.w2UWpY-EPuThe1gnSCFEk2qrA_AZzznuMQz0tdq5pIc';

  const initializePeerConnection = () => {
    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
    });
    console.log('Support: PeerConnection initialized');

    pc.onicecandidate = (event) => {
      if (event.candidate && stompClientRef.current && callIdRef.current) {
        console.log('Support: Sending ICE candidate:', event.candidate);
        stompClientRef.current.publish({
          destination: `/app/call/${callIdRef.current}/signal`,
          body: JSON.stringify({
            callId: callIdRef.current,
            type: 'ice-candidate',
            data: JSON.stringify(event.candidate),
            fromUserId: 2,
            toUserId: 1,
          }),
        });
      }
    };

    pc.ontrack = (event) => {
      console.log('Support: ontrack fired, streams:', event.streams);
      const stream = event.streams[0];
      console.log('Support: Stream details:', {
        id: stream.id,
        audioTracks: stream.getAudioTracks(),
        videoTracks: stream.getVideoTracks(),
        trackKind: event.track.kind,
      });

      if (event.track.kind === 'audio') {
        console.log('Support: Assigning audio stream');
        remoteStreamRef.current = stream;
        if (audioRef.current) {
          audioRef.current.srcObject = stream;
          audioRef.current.volume = volume;
          audioRef.current.muted = false;
          audioRef.current.play().catch(err => console.error('Support: Audio play error:', err));
        }
      } else if (event.track.kind === 'video') {
        console.log('Support: Assigning video stream');
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play().catch(err => console.error('Support: Video play error:', err));
          setIsScreenSharing(true);
        }
      }

      stream.onremovetrack = (event) => {
        console.log('Support: Track removed:', event.track);
        if (event.track.kind === 'video') {
          console.log('Support: Video track removed, stopping screen sharing');
          setIsScreenSharing(false);
          if (videoRef.current) videoRef.current.srcObject = null;
        }
      };
    };

    pc.onconnectionstatechange = () => {
      console.log('Support: Connection state:', pc.connectionState);
      if (pc.connectionState === 'failed') {
        console.error('Support: Connection failed');
        setCallStatus('ended');
        cleanupCall();
      }
    };

    peerConnectionRef.current = pc;
  };

  useEffect(() => {
    const socket = new SockJS('https://192.168.0.102:8082/ws', null, { timeout: 30000 });
    const client = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => console.log(str),
    });

    client.onConnect = (frame) => {
      console.log('Support: Connected to WebSocket:', frame);
      stompClientRef.current = client;

      const callSub = client.subscribe('/user/2/call/incoming', (message) => {
        const callNotification = JSON.parse(message.body);
        console.log('Support: Received incoming call:', callNotification);
        setIncomingCall(callNotification);
        callIdRef.current = callNotification.callId;
      });

      const signalSub = client.subscribe('/user/2/call/signal', (message) => {
        const signal = JSON.parse(message.body);
        if (signal.callId === callIdRef.current) {
          handleWebRTCSignal(signal);
        }
      });

      const endSub = client.subscribe('/user/2/call/end', (message) => {
        const notification = JSON.parse(message.body);
        if (notification.callId === callIdRef.current) {
          setCallStatus('ended');
          setIncomingCall(null);
          stopTimer();
          cleanupCall();
        }
      });

      setCallSubscription({ callSub, signalSub, endSub });
    };

    client.onStompError = (error) => console.error('Support: WebSocket STOMP error:', error);
    client.onWebSocketError = (error) => console.error('Support: WebSocket error:', error);
    client.onWebSocketClose = (event) => {
      console.error('Support: WebSocket closed:', event);
      stompClientRef.current = null;
    };

    client.activate();

    return () => {
      if (client) client.deactivate();
    };
  }, []);

  useEffect(() => {
    const fetchMessages = async () => {
      if (ticket) {
        try {
          const response = await axios.get(`https://192.168.0.102:8082/api/chat/messages/${ticket.id}`, {
            headers: { Authorization: `Bearer ${token}` },
          });
          setMessages(response.data.map((msg) => ({
            ...msg,
            content: msg.message,
            timestamp: msg.createdAt,
          })) || []);
          setTicketStatus(ticket.status);
          setPendingStatus(ticket.status);
        } catch (error) {
          console.error('Support: Error fetching messages:', error);
        }
      }
    };

    fetchMessages();

    if (stompClientRef.current && ticket) {
      if (subscription) subscription.unsubscribe();
      const newSubscription = stompClientRef.current.subscribe(`/topic/ticket/${ticket.id}`, (message) => {
        const receivedMessage = JSON.parse(message.body);
        setMessages((prev) => [...prev, { ...receivedMessage, content: receivedMessage.message, timestamp: receivedMessage.createdAt }]);
      });
      setSubscription(newSubscription);
    }

    return () => {
      if (subscription) subscription.unsubscribe();
    };
  }, [ticket]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const startTimer = (startTime) => {
    const start = new Date(startTime);
    timerRef.current = setInterval(() => {
      const now = new Date();
      setCallDuration(Math.floor((now - start) / 1000));
    }, 1000);
  };

  const stopTimer = () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  };

  const formatDuration = (seconds) => {
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const sendMessage = () => {
    if (newMessage.trim() && stompClientRef.current && ticket) {
      stompClientRef.current.publish({
        destination: `/app/ticket/${ticket.id}/sendMessage`,
        body: JSON.stringify({ ticketId: ticket.id, senderId: 2, senderType: 'SUPPORT', message: newMessage }),
      });
      setNewMessage('');
    }
  };

  const handleAcceptCall = async () => {
    if (!stompClientRef.current || !callIdRef.current) {
      console.error('Support: Cannot accept call: missing stompClient or callId');
      return;
    }

    initializePeerConnection();
    setCallStatus('connected');
    setIncomingCall(null);

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      localStreamRef.current = stream;
      stream.getTracks().forEach((track) => {
        console.log('Support: Adding audio track:', track);
        const sender = peerConnectionRef.current.addTrack(track, stream);
        console.log('Support: Audio sender added:', sender);
      });

      stompClientRef.current.publish({
        destination: `/app/call/${callIdRef.current}/respond`,
        body: JSON.stringify({ callId: callIdRef.current, accepted: true }),
      });
      startTimer(new Date());
    } catch (error) {
      console.error('Support: Error accepting call:', error);
      handleRejectCall();
    }
  };

  const handleRejectCall = () => {
    if (stompClientRef.current && callIdRef.current) {
      stompClientRef.current.publish({
        destination: `/app/call/${callIdRef.current}/respond`,
        body: JSON.stringify({ callId: callIdRef.current, accepted: false }),
      });
    }
    setIncomingCall(null);
    callIdRef.current = null;
    setCallStatus(null);
  };

  const handleWebRTCSignal = async (signal) => {
    console.log('Support: Received signal:', signal);
    try {
      if (!peerConnectionRef.current) {
        console.error('Support: PeerConnection not initialized');
        return;
      }

      if (signal.type === 'offer') {
        const remoteDesc = new RTCSessionDescription(JSON.parse(signal.data));
        console.log('Support: Setting remote description, SDP:', remoteDesc.sdp);
        await peerConnectionRef.current.setRemoteDescription(remoteDesc);

        const answer = await peerConnectionRef.current.createAnswer();
        await peerConnectionRef.current.setLocalDescription(answer);
        console.log('Support: Answer SDP:', answer.sdp);

        const transceivers = peerConnectionRef.current.getTransceivers();
        transceivers.forEach((transceiver) => {
          if (transceiver.receiver.track?.kind === 'video') {
            transceiver.direction = 'recvonly';
            console.log('Support: Set video receiver to recvonly');
          }
        });

        stompClientRef.current.publish({
          destination: `/app/call/${callIdRef.current}/signal`,
          body: JSON.stringify({ callId: callIdRef.current, type: 'answer', data: JSON.stringify(answer), fromUserId: 2, toUserId: 1 }),
        });
      } else if (signal.type === 'ice-candidate') {
        const candidate = new RTCIceCandidate(JSON.parse(signal.data));
        await peerConnectionRef.current.addIceCandidate(candidate);
        console.log('Support: Added ICE candidate:', candidate);
      }
    } catch (error) {
      console.error('Support: Error handling WebRTC signal:', error);
    }
  };

  const cleanupCall = () => {
    stopTimer();
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
    }
    if (peerConnectionRef.current) {
      peerConnectionRef.current.close();
      peerConnectionRef.current = null;
    }
    remoteStreamRef.current = null;
    callIdRef.current = null;
    setIsMuted(false);
    setIsOnHold(false);
    setVolume(1.0);
    setIsRecording(false);
    setIsScreenSharing(false);
    setPosition({ x: 0, y: 0 });
    setSize({ width: 640, height: 480 });
    setTimeout(() => setCallStatus(null), 2000);
  };

  const handleHangUp = () => {
    if (stompClientRef.current && callIdRef.current) {
      stompClientRef.current.publish({ destination: `/app/call/${callIdRef.current}/end`, body: JSON.stringify({ callId: callIdRef.current }) });
    }
    setCallStatus('ended');
    cleanupCall();
  };

  const handleMuteToggle = () => {
    if (localStreamRef.current) {
      const audioTrack = localStreamRef.current.getAudioTracks()[0];
      audioTrack.enabled = !audioTrack.enabled;
      setIsMuted(!audioTrack.enabled);
    }
  };

  const handleHoldToggle = () => {
    if (localStreamRef.current) {
      const audioTrack = localStreamRef.current.getAudioTracks()[0];
      audioTrack.enabled = !isOnHold;
      if (audioRef.current) audioRef.current.muted = isOnHold;
      setIsOnHold(!isOnHold);
    }
  };

  const handleVolumeChange = (e) => {
    const newVolume = parseFloat(e.target.value);
    setVolume(newVolume);
    if (audioRef.current) audioRef.current.volume = newVolume;
  };

  const handleRecordingToggle = () => {
    setIsRecording(!isRecording);
  };

  const getCallQuality = () => {
    return callDuration < 10 ? 'Good' : 'Excellent';
  };

  const statuses = ['Open', 'In Progress', 'Resolved', 'Closed'];

  // Dragging logic
  const handleDragMouseDown = (e) => {
    if (e.target.className.includes('drag-handle')) {
      setDragging(true);
      const rect = screenShareModalRef.current.getBoundingClientRect();
      setDragOffset({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      });
    }
  };

  const handleDragMouseMove = (e) => {
    if (dragging) {
      const newX = e.clientX - dragOffset.x;
      const newY = e.clientY - dragOffset.y;
      setPosition({ x: Math.max(0, newX), y: Math.max(0, newY) });
    }
  };

  const handleDragMouseUp = () => {
    setDragging(false);
  };

  // Resizing logic
  const handleResizeMouseDown = (e) => {
    setResizing(true);
    setResizeStart({ x: e.clientX, y: e.clientY });
    e.preventDefault();
  };

  const handleResizeMouseMove = (e) => {
    if (resizing) {
      const deltaX = e.clientX - resizeStart.x;
      const deltaY = e.clientY - resizeStart.y;
      setSize({
        width: Math.max(300, size.width + deltaX),
        height: Math.max(200, size.height + deltaY),
      });
      setResizeStart({ x: e.clientX, y: e.clientY });
    }
  };

  const handleResizeMouseUp = () => {
    setResizing(false);
  };

  useEffect(() => {
    if (dragging || resizing) {
      window.addEventListener('mousemove', dragging ? handleDragMouseMove : handleResizeMouseMove);
      window.addEventListener('mouseup', dragging ? handleDragMouseUp : handleResizeMouseUp);
    }
    return () => {
      window.removeEventListener('mousemove', handleDragMouseMove);
      window.removeEventListener('mousemove', handleResizeMouseMove);
      window.removeEventListener('mouseup', handleDragMouseUp);
      window.removeEventListener('mouseup', handleResizeMouseUp);
    };
  }, [dragging, resizing]);

  return (
    <div className="flex-1 flex flex-col h-full bg-white dark:bg-gray-800">
      {ticket ? (
        <>
          <div className="bg-white dark:bg-gray-800 p-4 border-b border-gray-200 dark:border-gray-700 shadow-sm flex justify-between items-center">
            <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-200">
              Ticket #{ticket.id} - {ticket.subject}
            </h2>
            <div className="relative">
              <button onClick={() => setIsStatusMenuOpen(!isStatusMenuOpen)} className="bg-blue-600 text-white px-3 py-1 rounded-lg hover:bg-blue-700">
                Status: {ticketStatus}
              </button>
              {isStatusMenuOpen && (
                <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-lg shadow-lg z-10">
                  <div className="p-2">
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Update Status</label>
                    <select
                      value={pendingStatus}
                      onChange={(e) => setPendingStatus(e.target.value)}
                      className="w-full p-1 border rounded-lg dark:bg-gray-600 dark:text-gray-200"
                    >
                      {statuses.map((status) => (
                        <option key={status} value={status}>{status}</option>
                      ))}
                    </select>
                    <button onClick={() => { setTicketStatus(pendingStatus); setIsStatusMenuOpen(false); }} className="w-full bg-blue-600 text-white px-3 py-1 rounded-lg hover:bg-blue-700 mt-2">
                      Update Status
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
          <div className="flex-1 p-4 overflow-y-auto">
            {messages.map((msg, index) => (
              <div key={index} className={`mb-4 flex ${msg.senderType === 'SUPPORT' ? 'justify-end' : 'justify-start'}`}>
                <div className="flex items-start space-x-2">
                  {msg.senderType !== 'SUPPORT' && (
                    <div className="w-8 h-8 rounded-full bg-gray-300 flex items-center justify-center text-gray-600">C</div>
                  )}
                  <div>
                    <div
                      className={`max-w-xs p-3 rounded-lg shadow-sm ${
                        msg.senderType === 'SUPPORT'
                          ? 'bg-blue-600 text-white'
                          : 'bg-gray-200 dark:bg-gray-600 text-gray-800 dark:text-gray-200'
                      }`}
                    >
                      <p className="text-sm">{msg.content || 'No content'}</p>
                      <p className="text-xs text-gray-400 mt-1">
                        {msg.timestamp ? formatDistanceToNow(new Date(msg.timestamp), { addSuffix: true }) : 'No timestamp'}
                      </p>
                    </div>
                  </div>
                  {msg.senderType === 'SUPPORT' && (
                    <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white">S</div>
                  )}
                </div>
              </div>
            ))}
            <div ref={messagesEndRef} />
          </div>
          <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-sm">
            <div className="flex items-center space-x-2">
              <button onClick={() => console.log('Voice call clicked')} className="p-2 text-gray-500 hover:text-blue-600" disabled={callStatus !== null}>
                <PhoneIcon className="w-5 h-5" />
              </button>
              <input
                type="text"
                value={newMessage}
                onChange={(e) => setNewMessage(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && sendMessage()}
                placeholder="Type your message..."
                className="flex-1 p-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-600 dark:bg-gray-600 dark:text-gray-200"
              />
              <button onClick={sendMessage} className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700">
                Send
              </button>
            </div>
          </div>

          {incomingCall && !callStatus && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
              <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-lg w-80">
                <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-200 mb-4">Incoming Call</h3>
                <p className="text-gray-600 dark:text-gray-400 mb-4">Incoming call for Ticket #{incomingCall.ticketId}</p>
                <div className="flex space-x-4">
                  <button onClick={handleAcceptCall} className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700">
                    Accept
                  </button>
                  <button onClick={handleRejectCall} className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700">
                    Reject
                  </button>
                </div>
              </div>
            </div>
          )}

          {callStatus && (
            <div className="fixed bottom-4 right-4 bg-white dark:bg-gray-800 p-4 rounded-lg shadow-lg w-64 z-50">
              <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-200 mb-2">Voice Call Controls</h3>
              <p className="text-gray-600 dark:text-gray-400 mb-4">
                {callStatus === 'connected' && `Duration: ${formatDuration(callDuration)}`}
                {callStatus === 'ended' && `Call Ended - Duration: ${formatDuration(callDuration)}`}
              </p>
              {callStatus !== 'ended' && (
                <div className="flex flex-col space-y-4">
                  <div className="flex space-x-2 flex-wrap">
                    <button onClick={handleMuteToggle} className={`px-3 py-1 rounded-lg ${isMuted ? 'bg-yellow-500' : 'bg-gray-500'} text-white`}>
                      {isMuted ? 'Unmute' : 'Mute'}
                    </button>
                    <button onClick={handleHoldToggle} className={`px-3 py-1 rounded-lg ${isOnHold ? 'bg-yellow-500' : 'bg-gray-500'} text-white`}>
                      {isOnHold ? 'Resume' : 'Hold'}
                    </button>
                    <button onClick={handleRecordingToggle} className={`px-3 py-1 rounded-lg ${isRecording ? 'bg-red-500' : 'bg-gray-500'} text-white`}>
                      {isRecording ? 'Stop Recording' : 'Record'}
                    </button>
                  </div>
                  <div className="flex items-center space-x-2">
                    <label className="text-sm text-gray-600">Volume:</label>
                    <input type="range" min="0" max="1" step="0.1" value={volume} onChange={handleVolumeChange} className="w-full" />
                  </div>
                  <button onClick={handleHangUp} className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700">
                    Hang Up
                  </button>
                </div>
              )}
              {callStatus === 'ended' && (
                <button onClick={() => setCallStatus(null)} className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 w-full">
                  Close
                </button>
              )}
              <audio ref={audioRef} autoPlay playsInline muted={false} />
            </div>
          )}

          {isScreenSharing && (
            <div
              ref={screenShareModalRef}
              className="absolute bg-white dark:bg-gray-800 rounded-lg shadow-lg z-50 overflow-hidden"
              style={{ left: position.x, top: position.y, width: `${size.width}px`, height: `${size.height}px` }}
            >
              <div
                className="drag-handle p-2 bg-gray-200 dark:bg-gray-700 cursor-move flex justify-between items-center"
                onMouseDown={handleDragMouseDown}
              >
                <span className="text-sm font-semibold text-gray-800 dark:text-gray-200">
                  Screen Share - Duration: {formatDuration(callDuration)}
                </span>
                <div>
                  <button onClick={handleHangUp} className="ml-2 px-2 py-1 bg-red-600 text-white rounded hover:bg-red-700">
                    End Call
                  </button>
                </div>
              </div>
              <div className="p-4 flex-1">
                <video ref={videoRef} autoPlay playsInline muted={false} className="w-full h-full object-contain rounded-lg border-2 border-red-500" />
              </div>
              <div
                className="absolute bottom-0 right-0 w-4 h-4 bg-gray-500 cursor-se-resize"
                onMouseDown={handleResizeMouseDown}
              />
            </div>
          )}
        </>
      ) : (
        <div className="flex-1 flex items-center justify-center">
          <p className="text-gray-500 dark:text-gray-400">Select a ticket to start chatting.</p>
        </div>
      )}
    </div>
  );
};

export default SupportChatWindow;
