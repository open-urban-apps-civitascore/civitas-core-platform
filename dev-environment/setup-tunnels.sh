#!/bin/bash
#
# SSH Tunnel Setup for Authorization PoC
#
# Run this script on your macOS host to forward all service ports
# from the Linux VM to localhost on your Mac.
#
# Usage:
#   bash setup-tunnels.sh [vm-hostname]        # Set up tunnels
#   bash setup-tunnels.sh --kill               # Kill existing tunnels only
#   bash setup-tunnels.sh -k                   # Kill existing tunnels only
#
# Default VM hostname: lima-default (adjust if using different VM)
#
# Services forwarded:
#   - Frontend:        http://localhost:3000
#   - Custom Viz:      http://localhost:3002
#   - Keycloak:        http://localhost:8080
#   - Portal Backend:  http://localhost:8089
#   - AuthZ Repository:http://localhost:8091
#   - OPA:             http://localhost:8181
#   - APISIX Gateway:  http://localhost:9080
#   - PostgreSQL:      localhost:5432
#

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Parse arguments
KILL_ONLY=false
VM_HOST="lima-default"

while [[ $# -gt 0 ]]; do
    case $1 in
        --kill|-k)
            KILL_ONLY=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS] [vm-hostname]"
            echo ""
            echo "Options:"
            echo "  -k, --kill     Kill existing tunnels and exit"
            echo "  -h, --help     Show this help message"
            echo ""
            echo "Default VM hostname: lima-default"
            echo ""
            echo "Examples:"
            echo "  $0                    # Set up tunnels to lima-default"
            echo "  $0 my-vm              # Set up tunnels to my-vm"
            echo "  $0 --kill             # Kill all existing tunnels"
            exit 0
            ;;
        -*)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
        *)
            VM_HOST="$1"
            shift
            ;;
    esac
done

echo
echo -e "${BLUE}==========================================${NC}"
if [ "$KILL_ONLY" = true ]; then
    echo -e "${BLUE}Kill SSH Tunnels - Authorization PoC${NC}"
else
    echo -e "${BLUE}SSH Tunnel Setup - Authorization PoC${NC}"
fi
echo -e "${BLUE}==========================================${NC}"
echo

if [ "$KILL_ONLY" = false ]; then
    echo "VM Host: $VM_HOST"
    echo
fi

# SSH ControlMaster setup - reuse one connection for all tunnels
CONTROL_PATH="/tmp/ssh-tunnel-$USER-$$"

# Cleanup function for control socket
cleanup_control() {
    if [ -S "$CONTROL_PATH" ]; then
        ssh -O exit -S "$CONTROL_PATH" "$VM_HOST" 2>/dev/null || true
        rm -f "$CONTROL_PATH"
    fi
}

# Function to kill existing tunnels
kill_tunnels() {
    echo "Cleaning up existing tunnels..."

    local killed_any=false

    # Kill any SSH process doing port forwarding to these ports
    for port in 3000 3002 8080 8089 8091 8181 9080 5432; do
        local pids=$(lsof -ti :$port 2>/dev/null)
        if [ -n "$pids" ]; then
            echo "  Killing tunnel on port $port..."
            echo "$pids" | xargs kill -9 2>/dev/null || true
            killed_any=true
        fi
    done

    # Also kill any backgrounded SSH with port forwarding
    if pkill -f "ssh -f -N" 2>/dev/null; then
        killed_any=true
    fi

    # Clean up any stale control sockets
    rm -f /tmp/ssh-tunnel-$USER-* 2>/dev/null

    sleep 1

    if [ "$killed_any" = true ]; then
        echo -e "${GREEN}✓ Tunnels killed${NC}"
    else
        echo -e "${YELLOW}No active tunnels found${NC}"
    fi

    echo
}

# Trap to cleanup on script exit (only when setting up tunnels)
if [ "$KILL_ONLY" = false ]; then
    trap cleanup_control EXIT
fi

# Kill existing tunnels
kill_tunnels

# If --kill option, exit here
if [ "$KILL_ONLY" = true ]; then
    echo -e "${GREEN}Done!${NC}"
    echo
    exit 0
fi

# Continue with tunnel setup...

# Set up tunnels (password prompt only once with ControlMaster)
echo "Setting up SSH tunnels..."
echo "Note: You'll be prompted for your password once"
echo

# First tunnel establishes the master connection
ssh -f -N -M -S "$CONTROL_PATH" -L 3000:localhost:3000 "$VM_HOST"
if [ $? -eq 0 ] && [ -S "$CONTROL_PATH" ]; then
    echo -e "${GREEN}✓ Frontend:   http://localhost:3000${NC}"
else
    echo -e "${YELLOW}✗ Failed to establish SSH connection${NC}"
    cleanup_control
    exit 1
fi

# Wait a moment for master connection to stabilize
sleep 1

# Remaining tunnels reuse the connection (no password prompt)
ssh -f -N -S "$CONTROL_PATH" -L 3002:localhost:3002 "$VM_HOST"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Custom Viz: http://localhost:3002${NC}"
else
    echo -e "${YELLOW}✗ Custom Viz tunnel failed - port may be in use${NC}"
fi

ssh -f -N -S "$CONTROL_PATH" -L 8080:localhost:8080 "$VM_HOST"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Keycloak:   http://localhost:8080${NC}"
else
    echo -e "${YELLOW}✗ Keycloak tunnel failed - port may be in use${NC}"
fi

ssh -f -N -S "$CONTROL_PATH" -L 8089:localhost:8089 "$VM_HOST"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Portal Backend: http://localhost:8089${NC}"
else
    echo -e "${YELLOW}✗ Portal Backend tunnel failed - port may be in use${NC}"
fi

ssh -f -N -S "$CONTROL_PATH" -L 8091:localhost:8091 "$VM_HOST"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ AuthZ Repository: http://localhost:8091${NC}"
else
    echo -e "${YELLOW}✗ AuthZ Repository tunnel failed - port may be in use${NC}"
fi

ssh -f -N -S "$CONTROL_PATH" -L 8181:localhost:8181 "$VM_HOST"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ OPA:        http://localhost:8181${NC}"
else
    echo -e "${YELLOW}✗ OPA tunnel failed - port may be in use${NC}"
fi

ssh -f -N -S "$CONTROL_PATH" -L 9080:localhost:9080 "$VM_HOST"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ APISIX:     http://localhost:9080/api/dataspaces${NC}"
else
    echo -e "${YELLOW}✗ APISIX tunnel failed - port may be in use${NC}"
fi

ssh -f -N -S "$CONTROL_PATH" -L 5432:localhost:5432 "$VM_HOST"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ PostgreSQL: localhost:5432${NC}"
else
    echo -e "${YELLOW}✗ PostgreSQL tunnel failed - port may be in use${NC}"
fi

# Don't cleanup control socket - leave it running for the tunnels
trap - EXIT

echo
echo -e "${BLUE}==========================================${NC}"
echo -e "${GREEN}Tunnels established!${NC}"
echo -e "${BLUE}==========================================${NC}"
echo
echo "Access services from your Mac:"
echo "  - Frontend:         http://localhost:3000"
echo "  - Custom Viz:       http://localhost:3002"
echo "  - Keycloak:         http://localhost:8080"
echo "  - Portal Backend:   http://localhost:8089"
echo "  - AuthZ Repository: http://localhost:8091"
echo "  - OPA:              http://localhost:8181"
echo "  - APISIX:           http://localhost:9080"
echo "  - PostgreSQL:       localhost:5432"
echo
echo "To check running tunnels:"
echo "  lsof -i :3000,:3002,:8080,:8089,:8091,:8181,:9080,:5432 | grep LISTEN"
echo
echo "To stop all tunnels:"
echo "  bash setup-tunnels.sh --kill"
echo "  # or manually:"
echo "  # pkill -f 'ssh -f -N' && rm -f /tmp/ssh-tunnel-$USER-*"
echo
