import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { formatDistanceToNow } from 'date-fns';
import { PhoneIcon, ArrowsPointingOutIcon, ChevronDownIcon } from '@heroicons/react/24/outline';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const ChatWindow = ({ ticket, onNewMessage }) => {
  const [messages, setMessages] = useState([]);
  const [newMessage, setNewMessage] = useState('');
  const [ticketId, setTicketId] = useState(null);
  const stompClientRef = useRef(null);
  const [isConnected, setIsConnected] = useState(false);
  const [subscription, setSubscription] = useState(null);
  const [callSubscription, setCallSubscription] = useState(null);
  const [callStatus, setCallStatus] = useState(null);
  const callIdRef = useRef(null);
  const [pendingCall, setPendingCall] = useState(null);
  const [isMuted, setIsMuted] = useState(false);
  const [volume, setVolume] = useState(1.0);
  const [callDuration, setCallDuration] = useState(0);
  const [isScreenSharing, setIsScreenSharing] = useState(false);
  const [showPreview, setShowPreview] = useState(true);
  const [isMinimized, setIsMinimized] = useState(false);
  const [barPosition, setBarPosition] = useState({ x: 20, y: 20 });
  const messagesEndRef = useRef(null);
  const localStreamRef = useRef(null);
  const screenStreamRef = useRef(null);
  const peerConnectionRef = useRef(null);
  const audioRef = useRef(null);
  const localVideoRef = useRef(null);
  const timerRef = useRef(null);
  const barDragRef = useRef(null);
  const [isBarDragging, setIsBarDragging] = useState(false);
  const [barDragOffset, setBarDragOffset] = useState({ x: 0, y: 0 });
  const [uid, setUid] = useState(localStorage.getItem('uid') || null);

  const getToken = () => {
    const urlParams = new URLSearchParams(window.location.search);
    const tokenFromUrl = urlParams.get('token');
    if (tokenFromUrl) {
      localStorage.setItem('jwtToken', tokenFromUrl);
      return tokenFromUrl;
    }
    return localStorage.getItem('jwtToken') || null;
  };

  const token = getToken();

  const getUserIdFromToken = () => {
    try {
      if (!token) throw new Error('No JWT token available');
      const [, payload] = token.split('.');
      const decodedPayload = atob(payload);
      const parsedPayload = JSON.parse(decodedPayload);
      return parsedPayload.sub;
    } catch (error) {
      console.error('Client: Error decoding JWT payload:', error);
      return null;
    }
  };

  const userId = getUserIdFromToken();

  useEffect(() => {
    if (ticket) {
      console.log('Client: Ticket clicked, ticket object:', ticket);
      console.log('Client: Possible ticket ID properties:', {
        id: ticket.id,
        ticketId: ticket.ticketId,
      });
      const id = ticket.id || ticket.ticketId || null;
      if (id) {
        console.log('Client: Storing ticket ID:', id);
        setTicketId(id);
      } else {
        console.warn('Client: No valid ticket ID found in ticket object');
        setTicketId(null);
      }
    } else {
      console.log('Client: No ticket selected');
      setTicketId(null);
    }
  }, [ticket]);

  const initializePeerConnection = async () => {
    if (!ticket?.supportTeamId) {
      console.error('Client: Cannot initialize PeerConnection: missing supportTeamId', { ticket });
      setCallStatus('ended');
      cleanupCall();
      return null;
    }

    const pc = new RTCPeerConnection({
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        {
          urls: 'turn:68.183.139.244:3478',
          username: 'ysn',
          credential: 'ysn123',
        },
      ],
    });
    console.log('Client: PeerConnection initialized for supportTeamId:', ticket.supportTeamId);

    pc.onicecandidate = (event) => {
      if (event.candidate && stompClientRef.current && callIdRef.current) {
        console.log('Client: Sending ICE candidate to supportTeamId:', ticket.supportTeamId);
        stompClientRef.current.publish({
          destination: `/app/call/${callIdRef.current}/signal`,
          body: JSON.stringify({
            callId: callIdRef.current,
            type: 'ice-candidate',
            data: JSON.stringify(event.candidate),
            fromUserId: userId,
            toUserId: ticket.supportTeamId,
          }),
        });
      }
    };

    pc.ontrack = (event) => {
      console.log('Client: ontrack fired, streams:', event.streams);
      const stream = event.streams[0];
      console.log('Client: Stream details:', {
        id: stream.id,
        audioTracks: stream.getAudioTracks(),
        videoTracks: stream.getVideoTracks(),
        trackKind: event.track.kind,
      });
      if (event.track.kind === 'audio' && audioRef.current) {
        console.log('Client: Assigning remote audio stream');
        audioRef.current.srcObject = stream;
        audioRef.current.volume = volume;
        audioRef.current.muted = false;
        audioRef.current.play().catch(err => console.error('Client: Audio play error:', err.message));
      }
    };

    pc.onconnectionstatechange = () => {
      console.log('Client: Connection state:', pc.connectionState);
      if (pc.connectionState === 'failed') {
        console.error('Client: Connection failed:', pc.connectionState);
        setCallStatus('ended');
        cleanupCall();
      }
    };

    try {
      const audioStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      localStreamRef.current = audioStream;
      audioStream.getTracks().forEach((track) => {
        console.log('Client: Adding audio track:', track);
        pc.addTrack(track, audioStream);
      });
    } catch (error) {
      console.error('Client: Error getting audio stream:', error);
      setCallStatus('ended');
      cleanupCall();
      return null;
    }

    peerConnectionRef.current = pc;
    return pc;
  };

  useEffect(() => {
    if (!token || !userId) {
      console.error('Client: No JWT token or userId available');
      return;
    }

    const socket = new SockJS('https://chat.prjsdr.xyz/ws', null, { timeout: 30000 });
    const client = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => console.log(`Client: STOMP: ${str}`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      onStompError: (frame) => console.error('Client: STOMP error:', frame),
      onWebSocketError: (error) => console.error('Client: WebSocket error:', error),
      onUnhandledMessage: (message) => console.log('Client: Unhandled STOMP message:', message),
      onUnhandledFrame: (frame) => console.log('Client: Unhandled STOMP frame:', frame),
    });

    client.onConnect = (frame) => {
      console.log('Client: Connected to WebSocket:', frame);
      stompClientRef.current = client;
      setIsConnected(true);

      const uidSub = client.subscribe(`/user/${userId}/uid`, (message) => {
        try {
          console.log('Client: Raw UID message:', message.body);
          const uidMessage = JSON.parse(message.body);
          console.log('Client: Parsed UID:', uidMessage.uid);
          if (uidMessage.uid) {
            setUid(uidMessage.uid);
            localStorage.setItem('uid', uidMessage.uid);
            console.log('Client: UID stored successfully:', uidMessage.uid);
          } else {
            console.warn('Client: UID message missing uid field:', uidMessage);
          }
        } catch (error) {
          console.error('Client: Error parsing UID message:', error, 'Raw message:', message.body);
        }
      });
      console.log('Client: Subscribed to /user/' + userId + '/uid with sub-id:', uidSub.id);
    };

    client.onWebSocketClose = (event) => {
      console.error('Client: WebSocket closed:', event);
      stompClientRef.current = null;
      setIsConnected(false);
    };

    client.activate();

    return () => {
      if (client) client.deactivate();
    };
  }, [token, userId]);

  useEffect(() => {
    if (!isConnected || !stompClientRef.current || !uid) {
      console.log('Client: Skipping message subscription due to missing requirements', {
        isConnected,
        stompClient: !!stompClientRef.current,
        uid,
      });
      return;
    }

    console.log('Client: Subscribing to messages with UID:', uid);
    const messageSub = stompClientRef.current.subscribe(`/user/${uid}/messages`, (message) => {
      try {
        const receivedMessage = JSON.parse(message.body);
        console.log('Client: Received WebSocket message:', receivedMessage);
        if (receivedMessage.ticketId === ticketId) {
          setMessages((prev) => [
            ...prev,
            {
              ...receivedMessage,
              content: receivedMessage.message,
              timestamp: receivedMessage.createdAt,
            },
          ]);
        }
        // Notify parent about new message for any ticket
        onNewMessage(receivedMessage.ticketId);
      } catch (error) {
        console.error('Client: Error parsing message:', error);
      }
    });
    setSubscription(messageSub);

    return () => {
      if (messageSub) {
        messageSub.unsubscribe();
        setSubscription(null);
      }
    };
  }, [isConnected, uid, ticketId, onNewMessage]);

  useEffect(() => {
    if (!isConnected || !stompClientRef.current || !ticketId) {
      console.log('Client: Skipping call subscriptions due to missing requirements', {
        isConnected,
        stompClient: !!stompClientRef.current,
        ticketId,
      });
      return;
    }

    console.log('Client: Setting up call subscriptions for ticket ID:', ticketId);

    const callSub = stompClientRef.current.subscribe(`/user/${userId}/ticket/${ticketId}/call/response`, (message) => {
      const response = JSON.parse(message.body);
      console.log('Client: Received call response for ticket ID:', ticketId, response);
      if (response.callId) {
        callIdRef.current = response.callId;
        if (response.accepted) {
          setCallStatus('connected');
          startTimer(new Date(response.timestamp));
          if (stompClientRef.current.active && ticket?.supportTeamId) {
            console.log('Client: Starting WebRTC call for ticket ID:', ticketId);
            startWebRTCCall();
          } else {
            console.warn('Client: Delaying WebRTC call due to missing ticket or inactive client', {
              ticket,
              clientActive: stompClientRef.current.active,
            });
            setPendingCall(response);
          }
        } else {
          setCallStatus('ended');
          cleanupCall();
        }
      }
    });

    const signalSub = stompClientRef.current.subscribe(`/user/${userId}/ticket/${ticketId}/call/signal`, (message) => {
      const signal = JSON.parse(message.body);
      if (signal.callId === callIdRef.current) {
        handleWebRTCSignal(signal);
      }
    });

    const endSub = stompClientRef.current.subscribe(`/user/${userId}/ticket/${ticketId}/call/end`, (message) => {
      const notification = JSON.parse(message.body);
      if (notification.callId === callIdRef.current) {
        setCallStatus('ended');
        stopTimer();
        cleanupCall();
      }
    });

    setCallSubscription({ callSub, signalSub, endSub });

    return () => {
      if (callSubscription) {
        callSubscription.callSub?.unsubscribe();
        callSubscription.signalSub?.unsubscribe();
        callSubscription.endSub?.unsubscribe();
        setCallSubscription(null);
      }
    };
  }, [isConnected, ticketId]);

  useEffect(() => {
    const fetchMessages = async () => {
      if (!ticketId || !token) {
        console.log('Client: No ticket ID or token available for fetching messages');
        return;
      }

      console.log('Client: Fetching messages for ticket ID:', ticketId);
      try {
        const response = await axios.get(`https://chat.prjsdr.xyz/api/chat/messages/${ticketId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        console.log('Client: Received messages:', response.data);
        setMessages(
          response.data.map((msg) => ({
            ...msg,
            content: msg.message,
            timestamp: msg.createdAt,
          })) || []
        );
      } catch (error) {
        console.error('Client: Error fetching messages:', error);
      }
    };

    fetchMessages();
  }, [ticketId, token]);

  useEffect(() => {
    if (isConnected && stompClientRef.current && pendingCall && ticket?.supportTeamId) {
      console.log('Client: Processing pending call for ticket ID:', ticketId);
      callIdRef.current = pendingCall.callId;
      setCallStatus('connected');
      startTimer(new Date(pendingCall.timestamp));
      startWebRTCCall();
      setPendingCall(null);
    }
  }, [isConnected, pendingCall, ticket]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    if (isScreenSharing && showPreview && localVideoRef.current && screenStreamRef.current && !isMinimized) {
      console.log('Client: Reassigning screen stream to local video');
      localVideoRef.current.srcObject = screenStreamRef.current;
      localVideoRef.current.play()
        .then(() => console.log('Client: Local video playback started'))
        .catch(err => console.error('Client: Local video play error:', err.message));
    }
  }, [isScreenSharing, showPreview, isMinimized]);

  const startTimer = (startTime) => {
    const start = new Date(startTime);
    timerRef.current = setInterval(() => {
      const now = new Date();
      setCallDuration(Math.floor((now - start) / 1000) - 3600);
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
    if (newMessage.trim() && stompClientRef.current && ticketId && userId && uid) {
      console.log('Client: Sending message for ticket ID:', ticketId);
      stompClientRef.current.publish({
        destination: `/app/messages/${uid}`,
        body: JSON.stringify({
          ticketId: ticketId,
          message: newMessage,
          jwtToken: `Bearer ${token}`,
        }),
      });
      setNewMessage('');
    }
  };

  const startWebRTCCall = async () => {
    if (!stompClientRef.current || !callIdRef.current || !peerConnectionRef.current || !ticket?.supportTeamId) {
      console.error('Client: Cannot start WebRTC call: missing requirements', {
        stompClient: !!stompClientRef.current,
        callId: !!callIdRef.current,
        peerConnection: !!peerConnectionRef.current,
        supportTeamId: !!ticket?.supportTeamId,
      });
      setCallStatus('ended');
      cleanupCall();
      return;
    }

    try {
      const offer = await peerConnectionRef.current.createOffer();
      await peerConnectionRef.current.setLocalDescription(offer);
      console.log('Client: Initial offer SDP:', offer.sdp);

      stompClientRef.current.publish({
        destination: `/app/call/${callIdRef.current}/signal`,
        body: JSON.stringify({
          callId: callIdRef.current,
          type: 'offer',
          data: JSON.stringify(offer),
          fromUserId: userId,
          toUserId: ticket.supportTeamId,
        }),
      });
    } catch (error) {
      console.error('Client: Error starting WebRTC call:', error.message);
      setCallStatus('ended');
      cleanupCall();
    }
  };

  const startScreenSharing = async () => {
    if (!peerConnectionRef.current || !stompClientRef.current || !callIdRef.current || !ticket?.supportTeamId) {
      console.error('Client: Cannot start screen sharing: missing requirements', {
        peerConnection: !!peerConnectionRef.current,
        stompClient: !!stompClientRef.current,
        callId: !!callIdRef.current,
        supportTeamId: !!ticket?.supportTeamId,
      });
      return;
    }

    try {
      console.log('Client: Requesting screen share via getDisplayMedia');
      const screenStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
      console.log('Client: getDisplayMedia succeeded, stream:', screenStream);
      screenStreamRef.current = screenStream;
      setIsScreenSharing(true);
      setShowPreview(true);

      const videoTrack = screenStream.getVideoTracks()[0];
      console.log('Client: Video track:', videoTrack);
      const videoSender = peerConnectionRef.current.addTrack(videoTrack, screenStream);
      console.log('Client: Video sender added:', videoSender);

      videoTrack.onended = () => {
        console.log('Client: Screen sharing stopped by user');
        stopScreenSharing();
      };

      const offer = await peerConnectionRef.current.createOffer();
      await peerConnectionRef.current.setLocalDescription(offer);
      console.log('Client: Offer SDP with screen share:', offer.sdp);

      stompClientRef.current.publish({
        destination: `/app/call/${callIdRef.current}/signal`,
        body: JSON.stringify({
          callId: callIdRef.current,
          type: 'offer',
          data: JSON.stringify(offer),
          fromUserId: userId,
          toUserId: ticket.supportTeamId,
        }),
      });

      if (localVideoRef.current) {
        console.log('Client: Assigning screen stream to local video');
        localVideoRef.current.srcObject = screenStream;
        localVideoRef.current.play()
          .then(() => console.log('Client: Local video playback started'))
          .catch(err => console.error('Client: Local video play error:', err.message));
      }
    } catch (error) {
      console.error('Client: Error in startScreenSharing:', error.message);
      setIsScreenSharing(false);
      setShowPreview(false);
      if (localVideoRef.current) localVideoRef.current.srcObject = null;
    }
  };

  const stopScreenSharing = async () => {
    if (screenStreamRef.current) {
      screenStreamRef.current.getTracks().forEach((track) => track.stop());
      screenStreamRef.current = null;
    }
    setIsScreenSharing(false);
    setShowPreview(false);
    if (localVideoRef.current) {
      localVideoRef.current.srcObject = null;
      console.log('Client: Cleared local video srcObject');
    }

    if (!peerConnectionRef.current || !stompClientRef.current || !callIdRef.current || !ticket?.supportTeamId) {
      console.error('Client: Cannot renegotiate after stopping screen share: missing requirements', {
        peerConnection: !!peerConnectionRef.current,
        stompClient: !!stompClientRef.current,
        callId: !!callIdRef.current,
        supportTeamId: !!ticket?.supportTeamId,
      });
      return;
    }

    try {
      const senders = peerConnectionRef.current.getSenders();
      const videoSenders = senders.filter((sender) => sender.track && sender.track.kind === 'video');
      videoSenders.forEach((sender) => {
        console.log('Client: Removing video sender:', sender);
        peerConnectionRef.current.removeTrack(sender);
      });

      const offer = await peerConnectionRef.current.createOffer();
      await peerConnectionRef.current.setLocalDescription(offer);
      console.log('Client: Offer SDP after stopping screen share:', offer.sdp);

      stompClientRef.current.publish({
        destination: `/app/call/${callIdRef.current}/signal`,
        body: JSON.stringify({
          callId: callIdRef.current,
          type: 'offer',
          data: JSON.stringify(offer),
          fromUserId: userId,
          toUserId: ticket.supportTeamId,
        }),
      });
    } catch (error) {
      console.error('Client: Error stopping screen sharing:', error.message);
    }
  };

  const handleWebRTCSignal = async (signal) => {
    console.log('Client: Received signal:', signal);
    try {
      if (!peerConnectionRef.current) {
        console.error('Client: PeerConnection not initialized');
        return;
      }

      if (signal.type === 'answer') {
        const remoteDesc = new RTCSessionDescription(JSON.parse(signal.data));
        await peerConnectionRef.current.setRemoteDescription(remoteDesc);
        console.log('Client: Set remote description from answer:', remoteDesc.sdp);
      } else if (signal.type === 'ice-candidate') {
        const candidate = new RTCIceCandidate(JSON.parse(signal.data));
        await peerConnectionRef.current.addIceCandidate(candidate);
        console.log('Client: Added ICE candidate:', candidate);
      }
    } catch (error) {
      console.error('Client: Error handling WebRTC signal:', error.message);
    }
  };

  const cleanupCall = () => {
    stopTimer();
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
    }
    if (screenStreamRef.current) {
      screenStreamRef.current.getTracks().forEach((track) => track.stop());
      screenStreamRef.current = null;
    }
    if (peerConnectionRef.current) {
      peerConnectionRef.current.close();
      peerConnectionRef.current = null;
    }
    if (localVideoRef.current) localVideoRef.current.srcObject = null;
    callIdRef.current = null;
    setIsMuted(false);
    setVolume(1.0);
    setIsScreenSharing(false);
    setShowPreview(false);
    setIsMinimized(false);
    setBarPosition({ x: 20, y: 20 });
    setTimeout(() => setCallStatus(null), 2000);
  };

  const handleVoiceCallClick = async () => {
    if (!stompClientRef.current || !ticketId || !userId || !ticket?.supportTeamId) {
      console.error('Client: Cannot initiate call: missing requirements', {
        stompClient: !!stompClientRef.current,
        ticketId,
        userId: !!userId,
        supportTeamId: !!ticket?.supportTeamId,
      });
      return;
    }

    console.log('Client: Initiating call for ticket ID:', ticketId);
    setCallStatus('ringing');
    const pc = await initializePeerConnection();
    if (!pc) return;

    const tempCallId = Date.now().toString();
    callIdRef.current = tempCallId;

    stompClientRef.current.publish({
      destination: `/app/ticket/${ticketId}/initiateCall`,
      body: JSON.stringify({
        ticketId: ticketId,
        callerId: userId,
        callerType: 'CLIENT',
        callId: tempCallId,
        jwtToken: `Bearer ${token}`,
      }),
    });
  };

  const handleHangUp = () => {
    console.log('Client: handleHangUp called');
    if (stompClientRef.current && callIdRef.current) {
      stompClientRef.current.publish({
        destination: `/app/call/${callIdRef.current}/end`,
        body: JSON.stringify({ callId: callIdRef.current }),
      });
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

  const handleVolumeChange = (e) => {
    const newVolume = parseFloat(e.target.value);
    setVolume(newVolume);
    if (audioRef.current) audioRef.current.volume = newVolume;
  };

  const getCallQuality = () => {
    return callDuration < 10 ? 'Good' : 'Excellent';
  };

  const handleBarDragStart = (e) => {
    if (barDragRef.current) {
      const rect = barDragRef.current.getBoundingClientRect();
      setBarDragOffset({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      });
      setIsBarDragging(true);
    }
  };

  const handleBarDragMove = (e) => {
    if (isBarDragging) {
      setBarPosition({
        x: Math.max(0, e.clientX - barDragOffset.x),
        y: Math.max(0, e.clientY - barDragOffset.y),
      });
    }
  };

  const handleBarDragEnd = () => {
    setIsBarDragging(false);
  };

  useEffect(() => {
    if (isBarDragging) {
      window.addEventListener('mousemove', handleBarDragMove);
      window.addEventListener('mouseup', handleBarDragEnd);
    }
    return () => {
      window.removeEventListener('mousemove', handleBarDragMove);
      window.removeEventListener('mouseup', handleBarDragEnd);
    };
  }, [isBarDragging]);

  if (!token) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-red-500">No authentication token provided. Please include a token in the URL.</p>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col h-full bg-light-bg dark:bg-dark-bg">
      {ticket ? (
        <>
          <div className="bg-light-bg dark:bg-dark-bg p-4 border-b border-gray-200 dark:border-gray-700 shadow-sm">
            <h2 className="text-lg font-semibold text-light-text dark:text-dark-text">
              Ticket #{ticketId} - {ticket.subject}
            </h2>
          </div>
          <div className="flex-1 p-4 overflow-y-auto">
            {messages.map((msg, index) => (
              <div key={index} className={`mb-4 flex ${msg.senderType === 'CLIENT' ? 'justify-end' : 'justify-start'}`}>
                <div className="flex items-start space-x-2">
                  {msg.senderType !== 'CLIENT' && (
                    <img src="/avatar-support.png" alt="Support Avatar" className="w-8 h-8 rounded-full" />
                  )}
                  <div>
                    <div
                      className={`max-w-xs p-3 rounded-lg shadow-sm ${
                        msg.senderType === 'CLIENT'
                          ? 'bg-primary text-white'
                          : 'bg-light-message dark:bg-dark-message text-light-text dark:text-dark-text'
                      }`}
                    >
                      <p className="text-sm">{msg.content || 'No content'}</p>
                      <p className="text-xs text-gray-300 dark:text-gray-400 mt-1">
                      {msg.timestamp ? formatDistanceToNow(new Date(new Date(msg.timestamp).getTime() + 3600000), { addSuffix: true }) : 'No timestamp'}
                      </p>
                    </div>
                  </div>
                  {msg.senderType === 'CLIENT' && (
                    <img src="/avatar-client.png" alt="Client Avatar" className="w-8 h-8 rounded-full" />
                  )}
                </div>
              </div>
            ))}
            <div ref={messagesEndRef} />
          </div>
          <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-light-bg dark:bg-dark-bg shadow-sm">
            <div className="flex items-center space-x-2">
              <button
                onClick={handleVoiceCallClick}
                className="p-2 text-gray-500 hover:text-primary"
                disabled={callStatus !== null || !ticket?.supportTeamId}
              >
                <PhoneIcon className="w-5 h-5" />
              </button>
              <input
                type="text"
                value={newMessage}
                onChange={(e) => setNewMessage(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && sendMessage()}
                placeholder="Type your message..."
                className="flex-1 p-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary dark:bg-dark-bg dark:text-dark-text"
              />
              <button onClick={sendMessage} className="bg-primary text-white px-4 py-2 rounded-lg hover:bg-blue-700">
                Send
              </button>
            </div>
          </div>

          {callStatus && !isMinimized && (
            <div className="fixed inset-0 flex items-center justify-center z-60 bg-black bg-opacity-50 backdrop-blur-sm">
              <div className="bg-gray-900 text-white p-6 rounded-xl shadow-2xl w-96 transform transition-all duration-300 scale-100">
                <div className="flex justify-between items-center mb-3">
                  <h3 className="text-lg font-bold">Call with Support</h3>
                  <div className="flex space-x-2">
                    <button onClick={() => setIsMinimized(true)} className="p-1 text-gray-400 hover:text-white">
                      <ChevronDownIcon className="w-5 h-5" />
                    </button>
                  </div>
                </div>
                <p className="text-sm text-gray-400 mb-4">
                  {callStatus === 'ringing' && 'Waiting for Support...'}
                  {callStatus === 'connected' && `Quality: ${getCallQuality()} | Duration: ${formatDuration(callDuration)}`}
                  {callStatus === 'ended' && 'Call Ended'}
                </p>
                {callStatus === 'connected' && isScreenSharing && showPreview && (
                  <div className="mb-4">
                    <div className="relative w-full h-48 bg-black rounded-lg overflow-hidden">
                      <video
                        ref={localVideoRef}
                        autoPlay
                        playsInline
                        muted
                        className="w-full h-full object-contain"
                        onError={(e) => console.error('Client: Preview video error:', e.target.error?.message)}
                      />
                      <div className="absolute top-2 left-2 bg-blue-600 text-white text-xs px-2 py-1 rounded">
                        Screen Sharing Active
                      </div>
                    </div>
                  </div>
                )}
                {callStatus !== 'ended' && (
                  <div className="flex flex-col space-y-3">
                    <div className="flex space-x-2">
                      <button
                        onClick={handleMuteToggle}
                        className={`flex-1 py-2 rounded-lg text-sm font-medium ${
                          isMuted ? 'bg-yellow-500 hover:bg-yellow-600' : 'bg-gray-600 hover:bg-gray-700'
                        } transition`}
                      >
                        {isMuted ? 'Unmute' : 'Mute'}
                      </button>
                      <button
                        onClick={startScreenSharing}
                        className={`flex-1 py-2 rounded-lg text-sm font-medium ${
                          isScreenSharing ? 'bg-blue-500 hover:bg-blue-600' : 'bg-gray-600 hover:bg-gray-700'
                        } transition`}
                        disabled={isScreenSharing}
                      >
                        Share Screen
                      </button>
                    </div>
                    {isScreenSharing && (
                      <div className="flex space-x-2">
                        <button
                          onClick={() => setShowPreview(!showPreview)}
                          className="flex-1 py-2 rounded-lg text-sm font-medium bg-blue-600 hover:bg-blue-700 transition"
                        >
                          {showPreview ? 'Hide Preview' : 'Show Preview'}
                        </button>
                        <button
                          onClick={stopScreenSharing}
                          className="flex-1 py-2 rounded-lg text-sm font-medium bg-red-600 hover:bg-red-700 transition"
                        >
                          Stop Sharing
                        </button>
                      </div>
                    )}
                    <div className="flex items-center space-x-2">
                      <span className="text-sm text-gray-400">Volume:</span>
                      <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.1"
                        value={volume}
                        onChange={handleVolumeChange}
                        className="w-full accent-blue-600"
                      />
                    </div>
                    <button
                      onClick={handleHangUp}
                      className="py-2 rounded-lg bg-red-600 hover:bg-red-700 text-sm font-medium transition"
                    >
                      End Call
                    </button>
                  </div>
                )}
                {callStatus === 'ended' && (
                  <button
                    onClick={() => setCallStatus(null)}
                    className="w-full py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-sm font-medium transition"
                  >
                    Close
                  </button>
                )}
                <audio ref={audioRef} autoPlay playsInline muted={false} />
              </div>
            </div>
          )}

          {callStatus && isMinimized && (
            <div
              ref={barDragRef}
              className="fixed bg-gray-700 text-white rounded-lg shadow-lg z-50 flex items-center justify-between px-4 py-2 w-64 transition-all duration-300"
              style={{ left: `${barPosition.x}px`, top: `${barPosition.y}px` }}
              onMouseDown={handleBarDragStart}
            >
              <span className="text-sm font-medium">Call with Support - {formatDuration(callDuration)}</span>
              <div className="flex space-x-2">
                <button onClick={() => setIsMinimized(false)} className="p-1 text-gray-400 hover:text-white">
                  <ArrowsPointingOutIcon className="w-5 h-5" />
                </button>
                <button onClick={handleHangUp} className="p-1 text-gray-400 hover:text-red-400">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
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

export default ChatWindow;