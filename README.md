# one-time

Volatile key-value store with one-time viewable values.


## Getting a certificate
This is how you get a certificate via let's encrypt
```sudo certbot certonly --preferred-challenges dns -d one-time.toniogela.dev --manual```
Create a full certificate
```cat fullchain.pem privkey.pem > fullcert.pem```
and box it with openssl and remember the prompted password
```openssl pkcs12 -export -in fullcert.pem -out fullchain.p12```