pip install requests

python .\flag.py `
  --base-url http://localhost:8080 `
  --csv "C:\Users\ialle\Desktop\project\MSG CTF PROJECT\MSG_CTF_BACK\test\generated_passwords.csv" `
  --challenge-id 1 `
  --flag test `
  --mode odd-per-team `
  --workers 8 `
  --sleep 0.15