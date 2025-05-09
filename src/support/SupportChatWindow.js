import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { formatDistanceToNow } from 'date-fns';
import { PhoneIcon, ArrowsPointingOutIcon, ArrowsPointingInIcon, ChevronDownIcon } from '@heroicons/react/24/outline';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const SupportChatWindow = ({ ticket }) => {
  const [messages, setMessages] = useState([]);
  const [newMessage, setNewMessage] = useState('');
  const [ticketStatus, setTicketStatus] = useState(ticket?.status || 'Open');
  const [isStatusMenuOpen, setIsStatusMenuOpen] = useState(false);
  const [pendingStatus, setPendingStatus] = useState(ticket?.status || 'Open');
  const [ticketId, setTicketId] = useState(null); // Store ticket ID
  const stompClientRef = useRef(null);
  const [subscription, setSubscription] = useState(null);
  const [callSubscription, setCallSubscription] = useState(null);
  const [incomingCall, setIncomingCall] = useState(null);
  const callIdRef = useRef(null);
  const callerIdRef = useRef(null);
  const [callStatus, setCallStatus] = useState(null);
  const [isMuted, setIsMuted] = useState(false);
  const [volume, setVolume] = useState(1.0);
  const [callDuration, setCallDuration] = useState(0);
  const [isScreenSharing, setIsScreenSharing] = useState(false);
  const [videoSize, setVideoSize] = useState({ width: 400, height: 225 });
  const [videoPosition, setVideoPosition] = useState({ x: 20, y: 20 });
  const [isMinimized, setIsMinimized] = useState(false);
  const [isVideoMinimized, setIsVideoMinimized] = useState(false);
  const [barPosition, setBarPosition] = useState({ x: 20, y: 20 });
  const [isFullscreen, setIsFullscreen] = useState(false);
  const messagesEndRef = useRef(null);
  const localStreamRef = useRef(null);
  const remoteStreamRef = useRef(null);
  const peerConnectionRef = useRef(null);
  const audioRef = useRef(null);
  const videoRef = useRef(null);
  const dragRef = useRef(null);
  const barDragRef = useRef(null);
  const timerRef = useRef(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isBarDragging, setIsBarDragging] = useState(false);
  const [isResizing, setIsResizing] = useState(false);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
  const [barDragOffset, setBarDragOffset] = useState({ x: 0, y: 0 });

  // Retrieve token from URL or localStorage
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

  // Extract support ID from JWT token
  const getSupportIdFromToken = () => {
    if (!token) {
      console.error('Support: No JWT token available');
      return null;
    }
    try {
      const tokenParts = token.split('.');
      if (tokenParts.length !== 3) {
        console.error('Support: Invalid JWT token format');
        return null;
      }
      const payload = JSON.parse(atob(tokenParts[1]));
      return payload.userId; // Adjust based on your JWT structure
    } catch (error) {
      console.error('Support: Error parsing JWT token:', error);
      return null;
    }
  };

  // Log ticket object and store ticket ID when ticket prop changes
  useEffect(() => {
    if (ticket) {
      console.log('Support: Ticket clicked, ticket object:', ticket);
      console.log('Support: Possible ticket ID properties:', {
        id: ticket.id,
        ticketId: ticket.ticketId,
      });
      // Store the ticket ID (try 'id' first, then 'ticketId', or adjust based on logs)
      const id = ticket.id || ticket.ticketId || null;
      if (id) {
        console.log('Support: Storing ticket ID:', id);
        setTicketId(id);
      } else {
        console.warn('Support: No valid ticket ID found in ticket object');
        setTicketId(null);
      }
    } else {
      console.log('Support: No ticket selected');
      setTicketId(null);
    }
  }, [ticket]);

  const initializePeerConnection = async () => {
    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
    });
    console.log('Support: PeerConnection initialized');

    pc.onicecandidate = (event) => {
      if (event.candidate && stompClientRef.current && callIdRef.current && callerIdRef.current) {
        console.log('Support: Sending ICE candidate to clientId:', callerIdRef.current);
        stompClientRef.current.publish({
          destination: `/app/call/${callIdRef.current}/signal`,
          body: JSON.stringify({
            callId: callIdRef.current,
            type: 'ice-candidate',
            data: JSON.stringify(event.candidate),
            fromUserId: getSupportIdFromToken(),
            toUserId: callerIdRef.current,
          }),
        });
      }
    };

    pc.ontrack = (event) => {
      console.log('Support: ontrack fired, streams:', event.streams);
      const stream = event.streams[0];
      remoteStreamRef.current = stream;
      console.log('Support: Stream details:', {
        id: stream.id,
        videoTracks: stream.getVideoTracks(),
        audioTracks: stream.getAudioTracks(),
        trackKind: event.track.kind,
      });

      if (event.track.kind === 'video') {
        console.log('Support: Video track detected');
        setIsScreenSharing(true);
      } else if (event.track.kind === 'audio') {
        console.log('Support: Audio track detected');
        if (audioRef.current) {
          audioRef.current.srcObject = stream;
          audioRef.current.volume = volume;
          audioRef.current.muted = false;
          audioRef.current.play()
            .then(() => console.log('Support: Audio playback started'))
            .catch(err => console.error('Support: Audio play error:', err.message));
        }
      }

      stream.onremovetrack = (event) => {
        console.log('Support: Track removed:', event.track);
        if (event.track.kind === 'video') {
          console.log('Support: Video track removed, stopping screen sharing');
          setIsScreenSharing(false);
          setIsVideoMinimized(false);
          if (videoRef.current) videoRef.current.srcObject = null;
        }
      };
    };

    pc.onconnectionstatechange = () => {
      console.log('Support: Connection state:', pc.connectionState);
      if (pc.connectionState === 'failed') {
        console.error('Support: Connection failed:', pc.connectionState);
        setCallStatus('ended');
        cleanupCall();
      }
    };

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      localStreamRef.current = stream;
      stream.getTracks().forEach((track) => {
        console.log('Support: Adding audio track:', track);
        pc.addTrack(track, stream);
      });
    } catch (error) {
      console.error('Support: Error getting audio stream:', error);
      return null;
    }

    peerConnectionRef.current = pc;
    return pc;
  };

  // WebSocket connection setup
  useEffect(() => {
    if (!token) {
      console.error('Support: No JWT token available');
      return;
    }

    const socket = new SockJS('https://192.168.0.102:8082/ws', null, { timeout: 30000 });
    const client = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => console.log(str),
      connectHeaders: { Authorization: `Bearer ${token}` },
    });

    client.onConnect = (frame) => {
      console.log('Support: Connected to WebSocket:', frame);
      stompClientRef.current = client;
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
  }, [token]);

  // Ticket-specific subscriptions using stored ticketId
  useEffect(() => {
    if (!stompClientRef.current || !ticketId) {
      console.log('Support: Skipping subscriptions due to missing requirements', {
        stompClient: !!stompClientRef.current,
        ticketId,
      });
      return;
    }

    console.log('Support: Setting up subscriptions for ticket ID:', ticketId);

    const supportId = getSupportIdFromToken();
    if (!supportId) {
      console.error('Support: Failed to extract supportId from token');
      return;
    }

    const callSub = stompClientRef.current.subscribe(`/user/${supportId}/ticket/${ticketId}/call/incoming`, (message) => {
      const callNotification = JSON.parse(message.body);
      console.log('Support: Received incoming call for ticket ID:', ticketId, callNotification);
      setIncomingCall(callNotification);
      callIdRef.current = callNotification.callId;
      callerIdRef.current = callNotification.callerId;
      console.log('Support: Set callerIdRef:', callerIdRef.current);
    });

    const signalSub = stompClientRef.current.subscribe(`/user/${supportId}/ticket/${ticketId}/call/signal`, (message) => {
      const signal = JSON.parse(message.body);
      if (signal.callId === callIdRef.current) {
        handleWebRTCSignal(signal);
      }
    });

    const responseSub = stompClientRef.current.subscribe(`/user/${supportId}/ticket/${ticketId}/call/response`, (message) => {
      const response = JSON.parse(message.body);
      console.log('Support: Received call response for ticket ID:', ticketId, response);
      if (response.callId === callIdRef.current && response.accepted) {
        setCallStatus('connected');
        startTimer(response.timestamp);
      }
    });

    const endSub = stompClientRef.current.subscribe(`/user/${supportId}/ticket/${ticketId}/call/end`, (message) => {
      const notification = JSON.parse(message.body);
      if (notification.callId === callIdRef.current) {
        setCallStatus('ended');
        stopTimer();
        cleanupCall();
      }
    });

    setCallSubscription({ callSub, signalSub, responseSub, endSub });

    const newSubscription = stompClientRef.current.subscribe(`/topic/ticket/${ticketId}`, (message) => {
      const receivedMessage = JSON.parse(message.body);
      console.log('Support: Received WebSocket message for ticket ID:', ticketId, receivedMessage);
      setMessages((prev) => [
        ...prev,
        { ...receivedMessage, content: receivedMessage.message, timestamp: receivedMessage.createdAt },
      ]);
    });
    setSubscription(newSubscription);

    return () => {
      if (callSubscription) {
        callSubscription.callSub?.unsubscribe();
        callSubscription.signalSub?.unsubscribe();
        callSubscription.responseSub?.unsubscribe();
        callSubscription.endSub?.unsubscribe();
        setCallSubscription(null);
      }
      if (subscription) {
        subscription.unsubscribe();
        setSubscription(null);
      }
    };
  }, [ticketId]);

  // Fetch messages
  useEffect(() => {
    const fetchMessages = async () => {
      if (ticketId && token) {
        try {
          const response = await axios.get(`https://192.168.0.102:8082/api/chat/messages/${ticketId}`, {
            headers: { Authorization: `Bearer ${token}` },
          });
          console.log('Support: Fetched messages for ticket ID:', ticketId, response.data);
          setMessages(
            response.data.map((msg) => ({
              ...msg,
              content: msg.message,
              timestamp: msg.createdAt,
            })) || []
          );
          setTicketStatus(ticket?.status || 'Open');
          setPendingStatus(ticket?.status || 'Open');
        } catch (error) {
          console.error('Support: Error fetching messages:', error);
        }
      }
    };

    fetchMessages();
  }, [ticketId, token, ticket]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    if (callStatus === 'connected' && remoteStreamRef.current) {
      if (videoRef.current && remoteStreamRef.current.getVideoTracks().length > 0) {
        console.log('Support: Assigning video stream to videoRef');
        videoRef.current.srcObject = remoteStreamRef.current;
        videoRef.current.play()
          .then(() => console.log('Support: Video playback started'))
          .catch(err => console.error('Support: Video play error:', err.message));
      }
      if (audioRef.current && remoteStreamRef.current.getAudioTracks().length > 0) {
        console.log('Support: Assigning audio stream to audioRef');
        audioRef.current.srcObject = remoteStreamRef.current;
        audioRef.current.volume = volume;
        audioRef.current.muted = false;
        audioRef.current.play()
          .then(() => console.log('Support: Audio playback started'))
          .catch(err => console.error('Support: Audio play error:', err.message));
      }
    }
  }, [callStatus, volume, isScreenSharing]);

  useEffect(() => {
    if (isScreenSharing && (!isVideoMinimized || !isMinimized) && videoRef.current && remoteStreamRef.current) {
      console.log('Support: Reassigning video stream to videoRef');
      videoRef.current.srcObject = remoteStreamRef.current;
      videoRef.current.play()
        .then(() => console.log('Support: Video playback started after reassignment'))
        .catch(err => console.error('Support: Video play error after reassignment:', err.message));
    }
  }, [isScreenSharing, isVideoMinimized, isMinimized]);

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
    if (newMessage.trim() && stompClientRef.current && ticketId) {
      console.log('Support: Sending message for ticket ID:', ticketId);
      stompClientRef.current.publish({
        destination: `/app/ticket/${ticketId}/sendMessage`,
        body: JSON.stringify({
          ticketId: ticketId,
          message: newMessage,
          jwtToken: `Bearer ${token}`,
        }),
      });
      setNewMessage('');
    }
  };

  const handleAcceptCall = async () => {
    if (!stompClientRef.current || !callIdRef.current) {
      console.error('Support: Cannot accept call: missing stompClient or callId');
      return;
    }

    console.log('Support: Accepting call for ticket ID:', ticketId);
    const pc = await initializePeerConnection();
    if (!pc) {
      handleRejectCall();
      return;
    }

    setCallStatus('connected');

    stompClientRef.current.publish({
      destination: `/app/call/${callIdRef.current}/respond`,
      body: JSON.stringify({
        callId: callIdRef.current,
        accepted: true,
        timestamp: new Date().toISOString(),
        jwtToken: `Bearer ${token}`,
      }),
    });
  };

  const handleRejectCall = () => {
    if (stompClientRef.current && callIdRef.current) {
      console.log('Support: Rejecting call for ticket ID:', ticketId);
      stompClientRef.current.publish({
        destination: `/app/call/${callIdRef.current}/respond`,
        body: JSON.stringify({
          callId: callIdRef.current,
          accepted: false,
          jwtToken: `Bearer ${token}`,
        }),
      });
    }
    setIncomingCall(null);
    callIdRef.current = null;
    callerIdRef.current = null;
    setCallStatus(null);
  };

  const handleWebRTCSignal = async (signal) => {
    console.log('Support: Received signal for ticket ID:', ticketId, signal);
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

        if (!callerIdRef.current) {
          console.error('Support: Cannot send answer - missing callerId');
          return;
        }

        stompClientRef.current.publish({
          destination: `/app/call/${callIdRef.current}/signal`,
          body: JSON.stringify({
            callId: callIdRef.current,
            type: 'answer',
            data: JSON.stringify(answer),
            fromUserId: getSupportIdFromToken(),
            toUserId: callerIdRef.current,
          }),
        });
        console.log('Support: Sent answer to clientId:', callerIdRef.current);
      } else if (signal.type === 'ice-candidate') {
        const candidate = new RTCIceCandidate(JSON.parse(signal.data));
        await peerConnectionRef.current.addIceCandidate(candidate);
        console.log('Support: Added ICE candidate:', candidate);
      }
    } catch (error) {
      console.error('Support: Error handling WebRTC signal:', error.message);
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
    callerIdRef.current = null;
    setIncomingCall(null);
    setIsMuted(false);
    setVolume(1.0);
    setIsScreenSharing(false);
    setIsVideoMinimized(false);
    setVideoSize({ width: 400, height: 225 });
    setVideoPosition({ x: 20, y: 20 });
    setBarPosition({ x: 20, y: 20 });
    setIsMinimized(false);
    setIsFullscreen(false);
    setTimeout(() => setCallStatus(null), 2000);
  };

  const handleHangUp = () => {
    if (stompClientRef.current && callIdRef.current) {
      console.log('Support: Hanging up call for ticket ID:', ticketId);
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
    if (videoRef.current && remoteStreamRef.current) {
      console.log('Support: Reassigning video stream after volume change');
      videoRef.current.srcObject = remoteStreamRef.current;
      videoRef.current.play()
        .then(() => console.log('Support: Video playback started after volume change'))
        .catch(err => console.error('Support: Video play error after volume change:', err.message));
    }
  };

  const getCallQuality = () => {
    return callDuration < 10 ? 'Good' : 'Excellent';
  };

  const handleDragStart = (e) => {
    if (dragRef.current) {
      const rect = dragRef.current.getBoundingClientRect();
      setDragOffset({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      });
      setIsDragging(true);
    }
  };

  const handleDragMove = (e) => {
    if (isDragging) {
      setVideoPosition({
        x: Math.max(0, e.clientX - dragOffset.x),
        y: Math.max(0, e.clientY - dragOffset.y),
      });
    }
  };

  const handleDragEnd = () => {
    setIsDragging(false);
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

  const handleResizeStart = (e) => {
    e.preventDefault();
    setIsResizing(true);
  };

  const handleResizeMove = (e) => {
    if (isResizing && dragRef.current) {
      const rect = dragRef.current.getBoundingClientRect();
      const newWidth = Math.max(200, Math.min(800, e.clientX - rect.left));
      setVideoSize({
        width: newWidth,
        height: newWidth * 9 / 16,
      });
    }
  };

  const handleResizeEnd = () => {
    setIsResizing(false);
  };

  const toggleFullscreen = () => {
    if (!dragRef.current) {
      console.error('Support: Cannot toggle fullscreen: dragRef is null');
      return;
    }
    if (!isFullscreen) {
      try {
        dragRef.current.requestFullscreen().then(() => {
          setIsFullscreen(true);
          console.log('Support: Entered fullscreen');
        }).catch(err => {
          console.error('Support: Fullscreen error:', err.message);
          alert('Failed to enter fullscreen. Please ensure the app is running over HTTPS.');
        });
      } catch (error) {
        console.error('Support: Fullscreen request failed:', error.message);
      }
    } else {
      try {
        document.exitFullscreen().then(() => {
          setIsFullscreen(false);
          console.log('Support: Exited fullscreen');
        }).catch(err => console.error('Support: Exit fullscreen error:', err.message));
      } catch (error) {
        console.error('Support: Exit fullscreen failed:', error.message);
      }
    }
  };

  useEffect(() => {
    if (isDragging || isResizing || isBarDragging) {
      window.addEventListener('mousemove', isDragging ? handleDragMove : isResizing ? handleResizeMove : handleBarDragMove);
      window.addEventListener('mouseup', isDragging ? handleDragEnd : isResizing ? handleResizeEnd : handleBarDragEnd);
    }
    return () => {
      window.removeEventListener('mousemove', handleDragMove);
      window.removeEventListener('mousemove', handleResizeMove);
      window.removeEventListener('mousemove', handleBarDragMove);
      window.removeEventListener('mouseup', handleDragEnd);
      window.removeEventListener('mouseup', handleResizeEnd);
      window.removeEventListener('mouseup', handleBarDragEnd);
    };
  }, [isDragging, isResizing, isBarDragging]);

  const statuses = ['Open', 'In Progress', 'Resolved', 'Closed'];

  if (!token) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-red-500">No authentication token provided. Please include a token in the URL.</p>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col h-full bg-white dark:bg-gray-800">
      {ticket ? (
        <>
          <div className="bg-white dark:bg-gray-800 p-4 border-b border-gray-200 dark:border-gray-700 shadow-sm flex justify-between items-center">
            <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-200">
              Ticket #{ticketId} - {ticket.subject || 'No Subject'}
            </h2>
            <div className="relative">
              <button onClick={() => setIsStatusMenuOpen(!isStatusMenuOpen)} className="bg-blue-600 text-white px-3 py-1 rounded-lg hover:bg-blue-700">
                Status: {ticketStatus}
              </button>
              {isStatusMenuOpen && (
                <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-lg shadow-lg z-70">
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
                    <button
                      onClick={() => {
                        setTicketStatus(pendingStatus);
                        setIsStatusMenuOpen(false);
                      }}
                      className="w-full bg-blue-600 text-white px-3 py-1 rounded-lg hover:bg-blue-700 mt-2"
                    >
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
              <button
                onClick={() => console.log('Support: Voice call clicked for ticket ID:', ticketId)}
                className="p-2 text-gray-500 hover:text-blue-600"
                disabled={callStatus !== null}
              >
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
            <div className="fixed inset-0 flex items-center justify-center z-70 bg-black bg-opacity-50 backdrop-blur-sm">
              <div className="bg-gray-900 text-white p-6 rounded-xl shadow-2xl w-96 transform transition-all duration-300 scale-100">
                <h3 className="text-lg font-bold mb-3">Incoming Call</h3>
                <p className="text-sm text-gray-400 mb-4">Ticket #{ticketId}</p>
                <div className="flex space-x-2">
                  <button
                    onClick={handleAcceptCall}
                    className="flex-1 py-2 bg-green-600 rounded-lg hover:bg-green-700 text-sm font-medium transition"
                  >
                    Accept
                  </button>
                  <button
                    onClick={handleRejectCall}
                    className="flex-1 py-2 bg-red-600 rounded-lg hover:bg-red-700 text-sm font-medium transition"
                  >
                    Reject
                  </button>
                </div>
              </div>
            </div>
          )}

          {callStatus && !isMinimized && (
            <div className="fixed inset-0 flex items-center justify-center z-70 bg-black bg-opacity-50 backdrop-blur-sm">
              <div className="bg-gray-900 text-white p-6 rounded-xl shadow-2xl w-96 transform transition-all duration-300 scale-100">
                <div className="flex justify-between items-center mb-3">
                  <h3 className="text-lg font-bold">Call with Client</h3>
                  <div className="flex space-x-2">
                    <button onClick={() => setIsMinimized(true)} className="p-1 text-gray-400 hover:text-white">
                      <ChevronDownIcon className="w-5 h-5" />
                    </button>
                  </div>
                </div>
                <p className="text-sm text-gray-400 mb-4">
                  {callStatus === 'connected' && `Quality: ${getCallQuality()} | Duration: ${formatDuration(callDuration)}`}
                  {callStatus === 'ended' && 'Call Ended'}
                </p>
                {callStatus === 'connected' && isScreenSharing && isVideoMinimized && (
                  <div className="mb-4">
                    <div className="relative w-full h-48 bg-black rounded-lg overflow-hidden">
                      <video
                        ref={videoRef}
                        autoPlay
                        playsInline
                        muted={false}
                        className="w-full h-full object-contain"
                        onError={(e) => console.error('Support: Modal video error:', e.target.error?.message)}
                      />
                      <div className="absolute top-2 right-2 flex space-x-2">
                        <button
                          onClick={() => setIsVideoMinimized(false)}
                          className="p-1 text-gray-400 hover:text-white bg-gray-800 bg-opacity-75 rounded"
                        >
                          <ArrowsPointingOutIcon className="w-4 h-4" />
                        </button>
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
                    </div>
                    <div className="flex items-center space-x-2">
                      <span className="text-sm text-gray-400">Volume:</span>
                      <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.1"
                        value={volume}
                        onChange={handleVolumeChange}
                        className="w-full accent-blue-500"
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
              <span className="text-sm font-medium">Call with Client - {formatDuration(callDuration)}</span>
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

          {isScreenSharing && !isVideoMinimized && (
            <div
              ref={dragRef}
              className="fixed bg-gray-800 rounded-lg shadow-xl z-50 overflow-hidden transition-all duration-300"
              style={{ left: `${videoPosition.x}px`, top: `${videoPosition.y}px`, width: `${videoSize.width}px`, height: `${videoSize.height + 40}px` }}
            >
              <div
                className="w-full h-8 bg-gray-700 rounded-t-lg cursor-move flex items-center justify-between px-2 text-gray-200 text-sm font-medium"
                onMouseDown={handleDragStart}
              >
                <span>Screen Share - {formatDuration(callDuration)}</span>
                <div className="flex space-x-2">
                  <button onClick={() => setIsVideoMinimized(true)} className="p-1 text-gray-400 hover:text-white">
                    <ChevronDownIcon className="w-4 h-4" />
                  </button>
                  <button onClick={toggleFullscreen} className="p-1 text-gray-400 hover:text-white">
                    {isFullscreen ? <ArrowsPointingInIcon className="w-4 h-4" /> : <ArrowsPointingOutIcon className="w-4 h-4" />}
                  </button>
                  <button
                    onClick={handleHangUp}
                    className="p-1 text-gray-400 hover:text-red-400"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              </div>
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted={false}
                className="w-full h-[calc(100%-40px)] object-contain bg-black rounded-b-lg"
                onError={(e) => console.error('Support: Video element error:', e.target.error?.message)}
              />
              <div
                className="absolute bottom-0 right-0 w-4 h-4 bg-blue-500 cursor-se-resize"
                onMouseDown={handleResizeStart}
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