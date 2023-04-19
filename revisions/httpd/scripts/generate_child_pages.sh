hostname > /var/www/html/hostname.html

TOKEN=`curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600"`
instance_id=`curl -H "X-aws-ec2-metadata-token: $TOKEN" -v http://169.254.169.254/latest/meta-data/instance-id`
echo $instance_id > /var/www/html/instance_id.html

files=`ls -l --block-size=M /var/www/html/`
 echo "<pre>${files}</pre>" > /var/www/html/file_list.html
date > /var/www/html/datetime.html