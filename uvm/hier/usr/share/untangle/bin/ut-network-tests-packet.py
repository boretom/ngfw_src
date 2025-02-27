#! /usr/bin/python3
import os
import getopt
from multiprocessing import Process
import re
import shlex
import signal
import subprocess
import sys
import time

if "@PREFIX@" != '':
    sys.path.insert(0, '@PREFIX@/usr/lib/python3/dist-packages')

Max_file_time = 4 * 60 * 60

##
## Capture all signals that should interrupt so
## the writer process can be properly stopped.
##
def signal_handler( signal, frame ):
    dump_writer.terminate()
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)
signal.signal(signal.SIGUSR1, signal_handler)
signal.signal(signal.SIGUSR2, signal_handler)
signal.signal(signal.SIGQUIT, signal_handler)
signal.signal(signal.SIGALRM, signal_handler)

##
## Timeout class used for with print() statements to handle
## case of stdout going away.
##
class timeout:
    def __init__(self, seconds=1, error_message='Timeout'):
        self.seconds = seconds
        self.error_message = error_message
    def __enter__(self):
        signal.alarm(self.seconds)
    def __exit__(self, type, value, traceback):
        signal.alarm(0)

##
## Create packet dump as a separate process.
##
class DumpWriter:
    def start( self, arguments, timeout, file_name, error_file_name ):
        args = shlex.split( arguments )
        args.insert( 0, tcpdump )
        args.append( "-w" )
        args.append( file_name )

        self.process = subprocess.Popen( args, stderr=open( error_file_name, 'wb'), stdout=open(os.devnull, 'wb'), text=True )

    def terminate(self):
        self.process.terminate()

##
## Read packet dump and output to stdout
##
class DumpReader:
    regex_timestamp_header = re.compile(r'^\d{1,2}:\d{1,2}:\d{1,2}\.\d+ ')

    def __init__( self, file_name, error_file_name ):
        self.dump_file_name = file_name
        self.error_file_name = error_file_name

        self.printed_header = False
        self.last_line_count = 0

    def header( self ):
        if self.printed_header == False and os.path.isfile( self.error_file_name ) == True:
            h = open( self.error_file_name )
            count = 0
            for line in h:
                count = count + 1

                with timeout(seconds=5):
                    print(line)
                if count == 1:
                    break;
            h.close()
            if count > 0:
                self.printed_header = True

    def read( self, search_string=None ):
        if self.printed_header == False:
            return

        if self.last_line_count == 0:
            show_lines = 1
        else:
            show_lines = 0

        if os.path.isfile( self.dump_file_name ) == False:
            return

        args = [
            tcpdump,
            '-n',
			'-r', self.dump_file_name
        ]
        if search_string is not None:
            args.append('-A')

        self.process = subprocess.Popen( args, stdout=subprocess.PIPE, stderr=open(os.devnull, 'wb'), text=True )
        line_count = 0
        current_header = None
        current_payload = ""
        for line in self.process.stdout:
            line_count = line_count + 1
            if show_lines == 0 and line_count == self.last_line_count:
                show_lines = 1
                continue

            if show_lines == 1:
                with timeout(seconds=5):
                    if search_string is None:
                        print(line.strip())
                    else:
                        if re.match(DumpReader.regex_timestamp_header, line):
                            if current_header is not None:
                                if search_string in current_payload:
                                    print(current_header)
                                    print(current_payload)
                            current_header = line.strip()
                            current_payload = ""
                        elif current_payload is not None:
                            current_payload += line.strip()

                    sys.stdout.flush()
        self.last_line_count = line_count

##
## Remove files in target directory that are older
## than now - Max_file_time
##
def cleanup( path ):
    if path == "" or path == "/":
        return
    earliest_time = time.time() - Max_file_time
    for file_name in os.listdir( path ):
        file_time = os.path.getmtime( path + "/" + file_name )
        if file_time < earliest_time:
            os.remove( path + "/" + file_name )

##
## Script usage
##
def usage():
    print("usage")
    print("--help\tShow usage")
    print("--timeout <sec>\tTime to run in seconds")
    print("--filename <filename>\tFile to write")
    print("--search_string <text>\tOnly show packets containing this string")
    print("--arguments <list>\tcpdump arguments")

##
## Main
##
def main(argv):
    global _debug
    global dump_reader
    global dump_writer
    global tcpdump
    _debug = False
    tcpdump = "/usr/bin/tcpdump"

    timeout = 5
    filename = "/tmp/network-tests"
    arguments = ""
    search_string = None

    try:
        opts, args = getopt.getopt( argv, "htfas:d", [ "help", "timeout=", "filename=", "arguments=", "search_string=", "debug" ] )
    except getopt.GetoptError:
        usage()
        sys.exit(2)

    for opt, arg in opts:
        if opt in ( "-h", "--help" ):
            usage()
            sys.exit()
        elif opt in ( "-d", "--debug" ):
            _debug = True
        elif opt in ( "-t", "--timeout" ):
            timeout = int(arg)
        elif opt in ( "-f", "--filename" ):
            filename = arg
        elif opt in ( "-t", "--arguments" ):
            arguments = arg
        elif opt in ( "-s", "--search_string" ):
            search_string = arg

    if _debug == True:
        print("timeout=" + str(timeout))
        print("filename=" + filename)
        print("arguments=" + arguments)

    path = "/".join( filename.split( "/" )[0:-1] )
    if os.path.isdir( path ) == False:
        os.makedirs( path )

    tcpdump_stderr_filename = filename + ".stderr"

    dump_reader = DumpReader( filename, tcpdump_stderr_filename )
    dump_writer = DumpWriter()
    dump_writer.start( arguments, timeout, filename, tcpdump_stderr_filename )

    while timeout > 0:
        dump_reader.header()
        dump_reader.read(search_string)

        timeout = timeout -1
        time.sleep(1)

    dump_writer.terminate()
    dump_reader.read()

    cleanup( path )

if __name__ == "__main__":
    main( sys.argv[1:] )

