from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("app_control", "0002_initial"),
    ]

    operations = [
        migrations.AlterField(
            model_name="permissionalert",
            name="permission",
            field=models.CharField(
                choices=[
                    ("ACCESS_FINE_LOCATION", "GPS Location"),
                    ("CAMERA", "Camera"),
                    ("READ_CONTACTS", "Contacts"),
                    ("READ_PHONE_STATE", "Phone State"),
                    ("BIND_ACCESSIBILITY_SERVICE", "Accessibility Service"),
                    ("BIND_VPN_SERVICE", "VPN Service"),
                    ("PRIVATE_DNS", "Private DNS (DNS filter bypass)"),
                ],
                max_length=50,
            ),
        ),
    ]
