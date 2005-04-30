/*
 * Copyright (c) 2003 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
#include "netcap_server.h"

#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/epoll.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <mvutil/debug.h>
#include <mvutil/errlog.h>
#include <mvutil/hash.h>
#include <mvutil/unet.h>
#include "libnetcap.h"
#include "netcap_globals.h"
#include "netcap_subscriptions.h"
#include "netcap_queue.h"
#include "netcap_hook.h"
#include "netcap_session.h"
#include "netcap_pkt.h"
#include "netcap_rdr.h"
#include "netcap_udp.h"
#include "netcap_tcp.h"
#include "netcap_icmp.h"
#include "netcap_interface.h"

#define EPOLL_INPUT_SET  EPOLLIN|EPOLLPRI|EPOLLERR|EPOLLHUP
#define EPOLL_OUTPUT_SET EPOLLOUT|EPOLLPRI|EPOLLERR|EPOLLHUP

#define EXIT 0xDEADD00D

#define _server_lock()   while(sem_wait(&_server_sem)!=0) perrlog("sem_wait")
#define _server_unlock() while(sem_post(&_server_sem)!=0) perrlog("sem_post")
#define _epoll_print_stat(revents) do{ errlog(ERR_WARNING,"Message Incoming revents = 0x%08x \n",(revents)); \
                                       errlog(ERR_WARNING,"EPOLLIN:%i EPOLLOUT:%i EPOLLHUP:%i EPOLLERR:%i EPOLLERR:%i \n", \
                                              (revents) & EPOLLIN,  (revents) & EPOLLOUT, (revents) & EPOLLHUP, \
                                              (revents) & EPOLLERR, (revents) & EPOLLERR); \
                                       } while(0)

/**
 * the types of events the server handles
 */
typedef enum {
    POLL_MESSAGE = 1,  /* poll type for message queue */
    POLL_TCP_INCOMING, /* poll type for accepting new tcp connections */
    POLL_UDP_INCOMING, /* poll type for accepting udp packets */
    POLL_QUEUE_INCOMING,/* poll type for accepting queued packets */
    POLL_TCP_WAITING   /* poll type for waiting on connections to complete */
} poll_type_t;
    
/**
 * epoll_info_t store auxillary information of the fd's in the _epoll_fd
 * each fd in _epoll_fd has a epoll_info (stored in _epoll_table)
 */
typedef struct epoll_info {
    /**
     * the fd of this epoll fd
     */
    int             fd;

    /**
     * the type of this epoll fd
     */
    poll_type_t     type;

    /**
     * the subscription pertaining to this epoll fd (if any)
     * used in type = {POLL_TCP_INCOMING,POLL_UDP_INCOMING,POLL_TCP_WAITING,POLLQ_QUEUE}
     */
    netcap_sub_t*          sub;
    
    /**
     * the tcp sessi pertaining to this epoll fd (if any)
     * used in type = {POLL_TCP_WAITING}
     */
    // netcap_tcp_sess_t* tcp_sess;
    
    netcap_session_t* netcap_sess;
    
} epoll_info_t;



static int  _handle_tcp_incoming (epoll_info_t* info, int revents, int sock );
static int  _handle_message (epoll_info_t* info, int revents);
static int  _handle_completion (epoll_info_t* info, int revents);
static int  _handle_udp (epoll_info_t* info, int revents, int sock );
static int  _handle_queue (epoll_info_t* info, int revents);

/* static int  _start_open_connection (struct in_addr* destaddr,u_short destport); */
static int  _epoll_info_add (int fd, int events, int type, netcap_sub_t* sub, netcap_session_t* netcap_sess);
static int  _epoll_info_del (epoll_info_t* info);
static int  _epoll_info_del_fd (int fd);

static int   _message_pipe[2]; 
static sem_t _server_sem;
static int   _epoll_fd;
static ht_t  _epoll_table;
static volatile int   _server_threads = 0;

int  netcap_server_init (void)
{
    if (sem_init(&_server_sem,0,1)<0) 
        return perrlog("sem_init");
    if (pipe(_message_pipe)<0) 
        return perrlog("pipe");
    if ((_epoll_fd = epoll_create(EPOLL_MAX_EVENT))<0)
        return perrlog("epoll_create");
    if (ht_init(&_epoll_table, EPOLL_MAX_EVENT+1, int_hash_func, int_equ_func, HASH_FLAG_KEEP_LIST)<0)
        return perrlog("ht_init");

    if (_epoll_info_add(_message_pipe[0], EPOLL_INPUT_SET,POLL_MESSAGE,NULL,NULL)<0)
        return perrlog("_epoll_info_add");
    if (_epoll_info_add(netcap_queue_get_sock(),EPOLL_INPUT_SET,POLL_QUEUE_INCOMING,NULL,NULL)<0)
        return perrlog("_epoll_info_add");

    return 0;
}

int  netcap_server_shutdown (void)
{
    int tries=0;
    int numtries = 20;

    while ( _server_threads > 0 && tries < numtries ) {
        if ( netcap_server_sndmsg(NETCAP_MSG_SHUTDOWN,NULL ) < 0 )
            errlog(ERR_CRITICAL,"Failed shutdown netcap server\n");

        usleep( 100000 );
        tries++;
    }
    if (tries == numtries) 
        errlog(ERR_WARNING,"Couldnt shutdown Netcap Server (%i Threads remain)\n",_server_threads);
        
    if (_epoll_info_del_fd(_message_pipe[0])<0) 
        perrlog("_epoll_info_del_fd");
    if (_epoll_info_del_fd(netcap_queue_get_sock())<0) 
        perrlog("_epoll_info_del_fd");

    if (ht_destroy(&_epoll_table)>0)
        errlog(ERR_WARNING,"Entries left in epoll table\n");
    if (sem_destroy(&_server_sem)<0) 
        perrlog("sem_destroy");

    if (close(_message_pipe[0])<0)
        perrlog("close");
    if (close(_message_pipe[1])<0)
        perrlog("close");
    if (close(_epoll_fd)<0)
        perrlog("close");
        
    return 0;
}

int  netcap_server (void)
{
    struct epoll_event events[EPOLL_MAX_EVENT];
    int i,num_events;

    _server_threads++;
 entry:
    _server_lock();

    while(1) {

        if ((num_events = epoll_wait(_epoll_fd,events,EPOLL_MAX_EVENT,-1)) < 0) {
            _server_unlock();
            perrlog("epoll_wait");
            usleep(10000); /* just in case - prevent spinning */
            goto entry;
        }

        /**
         * handle events
         */
        for(i=0;i<num_events;i++) {
            epoll_info_t* info = ht_lookup(&_epoll_table,(void*)events[i].data.fd);

            if (!info) {
                errlog(ERR_CRITICAL,"Constraint failed: epoll_info_t missing!\n");
                continue;
            }

            switch(info->type) {
            case POLL_MESSAGE:
                /* Thread exit point */
                if ( _handle_message(info, events[i].events) == EXIT ) return 0;
                    break;
            case POLL_TCP_INCOMING:
                _handle_tcp_incoming(info, events[i].events, events[i].data.fd );
                break;
            case POLL_UDP_INCOMING:
                _handle_udp(info, events[i].events, events[i].data.fd );
                break;
            case POLL_TCP_WAITING:
                _handle_completion(info, events[i].events);
                break;
            case POLL_QUEUE_INCOMING:
                _handle_queue(info, events[i].events);
                break;
            }
        }

        /**
         * wait on the sem's before restarting while(1) 
         */
        _server_lock();
    }
    
    _server_unlock();
    _server_threads--;
    return errlog(ERR_CRITICAL,"Statement should not be reached\n");
}

int  netcap_server_sndmsg (netcap_mesg_t msg, void* arg)
{
    char buf[sizeof(netcap_mesg_t)+sizeof(void*)];
    int nbyt;

    memcpy(buf,&msg,sizeof(netcap_mesg_t));
    memcpy(buf+sizeof(netcap_mesg_t),&arg,sizeof(void*));
           
    if ((nbyt = write(_message_pipe[1],buf,sizeof(buf)))<0) 
        return perrlog("write");
    if (nbyt < sizeof(buf)) 
        return errlog(ERR_CRITICAL,"truncated write\n");

    debug(10,"Server message (%i,%08x) sent\n",(int)msg,arg);

    return nbyt;
}


static int  _handle_message (epoll_info_t* info, int revents)
{
    char buf[sizeof(netcap_mesg_t)+sizeof(void*)];
    netcap_mesg_t* msg = (netcap_mesg_t*)buf;
    void** arg = (void**)(buf+sizeof(netcap_mesg_t));
    int ret = -1;
    
    if (!(revents & EPOLLIN)) {
        _epoll_print_stat(revents);
        _server_unlock();
        return -1;
    }

    ret = read(_message_pipe[0],&buf,sizeof(buf));

    debug(10,"Server message (%i,%08x) received\n",*msg,*arg);

    if (ret < sizeof(buf)) {
        perrlog("read");
    } else {
        netcap_sub_t* sub;
        
        switch(*msg) {

        case NETCAP_MSG_REFRESH:
            _server_unlock();
            break;

        case NETCAP_MSG_NULL:
            _server_unlock();
            errlog(ERR_CRITICAL,"Null Message Received\n");
            break;

        case NETCAP_MSG_ADD_SUB:
            sub = *arg;

            /* Nothing to do for antisubscribes */
            if (( sub->rdr.flags & NETCAP_FLAG_ANTI_SUBSCRIBE ) == 0 ) {
                if (sub->traf.protocol == IPPROTO_TCP) {
                    int c;
                    int size = sub->rdr.port_max - sub->rdr.port_min + 1;
                    
                    if ( sub->rdr.socks == NULL || size < 1 || sub->rdr.socks[0] == -1 ) {
                        errlog( ERR_CRITICAL, "For TCP, there must be at least one local\n" );
                    } else {
                        for ( c = 0 ; c < size ; c++ ) {
                            int fd = sub->rdr.socks[c];
                            debug( 11, "NETCAP: Inserting socket: %d\n", fd );
                            
                            if ( _epoll_info_add( fd, EPOLL_INPUT_SET, POLL_TCP_INCOMING, sub, NULL ) < 0 ) {
                                perrlog("_epoll_info_add");
                                break;
                            }
                        }
                    }
                }
                else if (sub->traf.protocol == IPPROTO_UDP) {
                    int size = sub->rdr.port_max - sub->rdr.port_min + 1;
                    int fd;
                    
                    if ( sub->rdr.socks == NULL || size < 1 || sub->rdr.socks[0] == -1 ) {
                        errlog( ERR_CRITICAL, "For UDP, there must be at least one local\n" );
                    } else {
                        fd = sub->rdr.socks[0];
                        
                        if ( _epoll_info_add( fd, EPOLL_INPUT_SET, POLL_UDP_INCOMING, sub, NULL ) < 0 ) {
                            _server_unlock();
                            perrlog("_epoll_info_add");
                        }
                    }
                }
                else if (sub->traf.protocol == IPPROTO_ICMP ||
                         sub->traf.protocol == IPPROTO_ALL) {
                    /* no epoll info added */
                } else {
                    errlog(ERR_CRITICAL,"Unknown Protocol: %i\n",sub->traf.protocol);
                }
            }
            _server_unlock();
            break;
            
        case NETCAP_MSG_REM_SUB:
            sub = *arg;

            if (!sub)
                errlogargs();
            else {
                int c;

                if ( sub->rdr.socks != NULL ) {
                    int size = sub->rdr.port_max - sub->rdr.port_min + 1;
                    int fd;
                    
                    debug( 11, "Removing (%08x,%d,%d)", sub->rdr.socks, sub->rdr.port_min, sub->rdr.port_max );

                    for ( c = 0 ; c < size ; c++ ) {
                        fd = sub->rdr.socks[c];
                        debug( 11, "Removing socket[%d]: %d\n",c , fd );
                        
                        if ( fd != -1 && _epoll_info_del_fd( fd ) < 0 ) {
                            perrlog("_epoll_info_del_fd");
                        }
                    }
                }
            
                netcap_traffic_destroy( &sub->traf );
                rdr_destroy( &sub->rdr );
                subscription_raze( sub );
            }
             
            _server_unlock();
            break;

        case NETCAP_MSG_SHUTDOWN:
            debug(5,"NETCAP: Shutdown Received, Thread Terminating\n");
            _server_unlock();
            netcap_server_sndmsg(NETCAP_MSG_SHUTDOWN,NULL); /* tell the next guy to exit */

            _server_threads--;
            return EXIT;
            break;
                    
        default:
            _server_unlock();
            errlog(ERR_CRITICAL,"Unknown message: %i arg:%08x\n",*msg,*arg);
            break;
        }
    }

    return 0;
}

static int  _handle_tcp_incoming (epoll_info_t* info, int revents, int fd )
{
    struct sockaddr_in cli_addr;
    int cli_addrlen = sizeof(cli_addr);
    int cli_sock;
    netcap_sub_t* sub;

    if (!info || !info->sub) {
        _server_unlock();
        return errlogargs();
    }

    if (!(revents & EPOLLIN)) {
        _epoll_print_stat(revents);
        _server_unlock();
        return -1;
    }

    sub = info->sub;

    /**
     * Accept the connection 
     */
    cli_sock = accept( fd , (struct sockaddr *) &cli_addr, &cli_addrlen);
    if (cli_sock < 0) {
        _server_unlock();
        return perrlog("accept");
    }

    /**
     * if they only want a half complete connection 
     */
    if (sub->rdr.flags & NETCAP_FLAG_SRV_UNFINI) {
        _server_unlock();
        if (netcap_tcp_accept_hook(cli_sock,cli_addr,sub)<0) {
            if (close(cli_sock)<0)
                perrlog("close");
        }
        return 0;
    }


    errlog(ERR_CRITICAL,"non UNFINI mode is UNSUPPORTED at the moment.\n");
    _server_unlock();
    return -1;
    
    /**
     * otherwise start completeing the connection
     */
/*     if ((srv_sock = _start_open_connection(&dst.addr,dst_port))<0) { */
/*         errlog(ERR_WARNING,"Error completing connection %s:%i -> ",inet_ntoa(&cli_addr.sin_addr),ntohs(cli_addr.sin_port)); */
/*         errlog(ERR_WARNING,"%s:%i   %s\n",inet_ntoa(dst_addr),dst_port,strerror(errno)); */
/*         if (close(cli_sock)<0) perrlog("close"); */
/*         _server_unlock(); */
/*         return -1; */
/*     } */
    
    /**
     * add the new connection it to the epoll list (wait on it to complete)
     */
/*     if (_epoll_info_add(srv_sock,EPOLL_OUTPUT_SET,POLL_TCP_WAITING,info->sub,netcap_sess)<0) { */
/*         netcap_tcp_session_raze(1, netcap_sess); */
/*         if (close(cli_sock)<0) perrlog("close"); */
/*         _server_unlock(); */
/*         return errlog(ERR_CRITICAL,"Unable to add new epollinfo_t\n"); */
/*     } */
    
/*     _server_unlock(); */
/*     return 0; */
}

static int  _handle_completion (epoll_info_t* info, int revents)
{
    int            result      = -1;
    int            result_size = sizeof(result);
    int            flags;
    rdr_t* rdr;
    netcap_sub_t* sub;
    netcap_session_t*  netcap_sess;

    _server_unlock();

    /* FIXME */
    return errlog(ERR_CRITICAL,"Unimplemented\n");
    
    /**
     * Sanity checks
     */
    if (!info || !info->sub || !info->netcap_sess) {
        debug(1,"0x%08x 0x%08x 0x%08x\n",info,info->sub,info->netcap_sess);
        _server_unlock();
        return errlogargs();
    }

    sub = info->sub;
    rdr = &sub->rdr;
    netcap_sess = info->netcap_sess;
    
    if (!(revents & EPOLLOUT)) {
        netcap_tcp_session_debug(netcap_sess, 8, "Unable to Complete Connection");

        // This should close and then free the session
        netcap_tcp_session_raze(1, netcap_sess);
        _server_unlock();
        return 0;
    }

    /**
     * first remove it from the epoll list 
     * then release the lock
     */
    _epoll_info_del(info);
    _server_unlock();

    /**
     * set server socket back to a blocking socket
     */
    if ((flags = fcntl(netcap_sess->server_sock,F_GETFL))<0) {
        netcap_tcp_session_raze(1, netcap_sess);
        return perrlog("fcntl");
    }
    if (fcntl(netcap_sess->server_sock,F_SETFL,flags & (O_NDELAY ^ 0xffffffff))<0) {
        netcap_tcp_session_raze(1, netcap_sess);
        return perrlog("fcntl");
    }
    
    /**
     * check if the connection finished
     */
    if (getsockopt(netcap_sess->server_sock,SOL_SOCKET,SO_ERROR,&result,&result_size)<0) {
        netcap_tcp_session_raze(1, netcap_sess);
        return perrlog("getsockopt");
    }
    
    /**
     * if the connection could not be completed 
     * pass it to the hook anyway 
     * usually this is never result as POLL_HUP will be set by poll 
     * and this handled elswhere
     */
    if (result) {
        netcap_tcp_session_debug(netcap_sess, 8, "Unable to Complete Connection");

        /* close just the server sock and set it to -2 */
        if (close(netcap_sess->server_sock)<0) {
            perrlog("close");
        }

/*         netcap_sess->server_sock = -2; */
/*         netcap_tcp_call_hooks(NULL, netcap_sess,sub->arg); */
        return 0;
    }

    /**
     * otherwise the connection is ready to be passed up
     */
    else {
        netcap_tcp_session_debug(netcap_sess,8,"Completed Connection");
/*         netcap_tcp_call_hooks(NULL,netcap_sess,sub->arg); */
        return 0;
    }
    
}

static int  _handle_udp (epoll_info_t* info, int revents, int sock )
{
    netcap_pkt_t* pkt;
    int              len;
    char*            buf;
    rdr_t* rdr;
    netcap_sub_t* sub;

    /**
     * Sanity checks
     */
    if (!info || !info->sub) {
        _server_unlock();
        return errlogargs();
    }

    sub = info->sub;
    rdr = &sub->rdr;
    
    if (!(revents & EPOLLIN)) {
        _epoll_print_stat(revents);
        _server_unlock();
        return -1;
    }

    buf = malloc(UDP_MAX_MESG_SIZE);   
    if (!buf) {
        _server_unlock();
        return errlogmalloc();
    }
    pkt = netcap_pkt_create();
    if (!pkt) {
        free(buf);
        _server_unlock();
        return errlogmalloc();
    }

    /**
     * read the packet 
     */
    len = netcap_udp_recvfrom( sock, buf, UDP_MAX_MESG_SIZE, 0, pkt );

    if (len <= 0) {
        if ( len < 0 ) errlog( ERR_CRITICAL, "netcap_udp_recvfrom\n" );

        free(buf);
        free(pkt);
        _server_unlock();
        return -1;
    }
    
    pkt->data = buf;
    pkt->data_len = len;

    debug(10,"Got UDP Packet from: %s:%i\n",inet_ntoa(pkt->src.host),pkt->src.port);
    
    /**
     * unlock the server
     */
    _server_unlock();

    // Check to see if the session already exists
    return netcap_udp_call_hooks(pkt,sub->arg);
}

static int  _handle_queue (epoll_info_t* info, int revents)
{
    netcap_pkt_t* pkt;
    char*         buf;
    int           len;

    /**
     * Sanity checks
     */
    if (!info) {
        _server_unlock();
        return errlogargs();
    }

    if (info->fd != netcap_queue_get_sock()) {
        _server_unlock();
        return errlog(ERR_CRITICAL,"Constraint failed.\n");
    }

    if (!(revents & EPOLLIN)) {
        _epoll_print_stat(revents);
        _server_unlock();
        return -1;
    }

    buf = malloc(QUEUE_MAX_MESG_SIZE);
    if (!buf) {
        _server_unlock();
        return errlogmalloc();
    }
    pkt = netcap_pkt_create();
    if (!pkt) {
        free(buf);
        _server_unlock();
        return errlogmalloc();
    }
    
    /**
     * read the packet 
     */
    len = netcap_queue_read(buf, QUEUE_MAX_MESG_SIZE, pkt);
    if (len <= 0) {
        if (len<0) perrlog("netcap_queue_read");
        free(buf);
        netcap_pkt_raze(pkt);
        _server_unlock();
        return -1;
    }
    
    debug(10,"Got QUEUE Packet from: %s\n",inet_ntoa(pkt->src.host));
    
    /**
     * unlock the server
     */
    _server_unlock();

    if (pkt->proto == IPPROTO_ICMP) {
        /* XXX no arg because unknown sub
         * RBS: 04/13/05: args are never used, and probably could be eliminated */
        return netcap_icmp_call_hook( pkt );
    } else if (pkt->proto == IPPROTO_TCP) {
        return global_tcp_syn_hook( pkt );
    } else if ( pkt->proto == IPPROTO_UDP ) {
        /* XXX no arg because unknown sub
         * RBS: 04/13/05: args are never used, and probably could be eliminated */
        return netcap_udp_call_hooks( pkt, NULL );
    }
    else
        return errlog(ERR_CRITICAL,"Unknown protocol from QUEUE\n");
}

static int  _epoll_info_add (int fd, int events, int type, netcap_sub_t* sub, netcap_session_t* netcap_sess)
{
    epoll_info_t* info;
    struct epoll_event ev;

    if (fd == -1) return errlogargs();
    
    info = malloc(sizeof(epoll_info_t));
    if (!info)
        return errlogmalloc();

    info->fd    = fd;
    info->type  = type;
    info->sub   = sub;
    info->netcap_sess = netcap_sess;
    
    bzero(&ev,sizeof(struct epoll_event));
    ev.data.fd = fd;
    ev.events  = events;

    if (epoll_ctl(_epoll_fd,EPOLL_CTL_ADD,fd,&ev)<0)
        return perrlog("epoll_ctl");

    if (ht_add(&_epoll_table,(void*)fd,(void*)info)<0)
        return perrlog("ht_add");

    return 0;
}

static int  _epoll_info_del_fd (int fd)
{
    epoll_info_t* epi = ht_lookup(&_epoll_table,(void*)fd);

    if (!epi)
        return errlog(ERR_CRITICAL, "epoll_info_t for %d not found\n", fd );

    return _epoll_info_del(epi);
}

static int  _epoll_info_del (epoll_info_t* info)
{
    struct epoll_event ev;

    if (info->fd == -1)
        return errlogargs();

    if (!info)
        return errlog(ERR_CRITICAL,"Invalid argument");

    bzero(&ev,sizeof(struct epoll_event));
    ev.data.fd = info->fd;
    ev.events  = 0;

    if (epoll_ctl(_epoll_fd,EPOLL_CTL_DEL,ev.data.fd,&ev)<0)
        return perrlog("epoll_ctl");

    if (ht_remove(&_epoll_table,(void*)info->fd)<0)
        return perrlog("ht_remove");

    free(info);
    
    return 0;
}

/* static int  _start_open_connection (struct in_addr* destaddr, u_short destport) */
/* { */
/*     int newsocket = unet_open(&destaddr->s_addr,destport); */
/*     int flags; */
    
/*     if (newsocket<0) return perrlog("unet_open"); */

/*     if ((flags = fcntl(newsocket,F_GETFL)) < 0) return  perrlog("fcntl"); */

/*     if (fcntl(newsocket, F_SETFL, flags | O_NDELAY) < 0) return perrlog("fcntl");  */

/*     return newsocket; */
/* } */
