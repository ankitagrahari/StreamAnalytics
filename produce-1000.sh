# Send 1000 events
for i in {1..10}; do
  curl "http://localhost:8085/produce?events=100"
  sleep 1
done