# Health Connect permissions declaration

Source text for the Play Console health apps declaration (App content › Health apps, Health Connect permissions). It lists every permission Tidemark requests, the visible feature that uses it, and a justification in English and French to paste into the form.

Check the current form wording when you submit. Field names and length limits change, and this file follows the app, not the form. The health features to tick on the first page of the form are in [console-answers.md](console-answers.md#health-apps).

## Summary for the reviewer

Tidemark is a read-only, offline viewer for Health Connect. It shows the user's own data on a dashboard with trend charts and in a record explorer that lists raw records with their metadata. It has no network permission, writes nothing to Health Connect and reads nothing in the background.

French: Tidemark est une visionneuse hors ligne, en lecture seule, pour Connexion Santé. Elle affiche les données de l'utilisateur sur un tableau de bord avec des graphiques de tendance et dans un explorateur d'enregistrements qui liste les enregistrements bruts avec leurs métadonnées. Elle n'a pas l'autorisation d'accès au réseau, n'écrit rien dans Connexion Santé et ne lit rien en arrière-plan.

## Where to find each feature

- **Dashboard:** the first screen after onboarding. Six tiles: steps, active calories and distance for today, last night's sleep, the latest resting heart rate and the latest weight.
- **Metric detail:** tap a tile. Day, week, month and year charts, with previous and next buttons.
- **Resting heart rate day view:** tap the resting heart rate tile, then choose Day. It plots that day's heart rate samples with the resting value as a reference line.
- **Sleep day view:** tap the sleep tile, then choose Day. It shows a hypnogram of that night's sleep stages.
- **Record explorer:** the explorer entry in the dashboard's top bar. Pick a type, then a record, to see every field and its metadata.
- **Privacy policy:** Settings › Privacy policy, and the links Health Connect shows in its permission screens.

## Permissions

### READ_STEPS

`android.permission.health.READ_STEPS`

Visible features: the steps tile with its 7-day chart, the steps detail charts, and steps records in the explorer.

- **EN:** Tidemark shows the user's step count for today on the dashboard, with a 7-day trend, and in day, week, month and year charts. The record explorer lists the user's individual step records with their source app. Data is only displayed on the device.
- **FR :** Tidemark affiche le nombre de pas du jour sur le tableau de bord, avec la tendance sur 7 jours, ainsi que dans des graphiques par jour, semaine, mois et année. L'explorateur liste chaque enregistrement de pas avec son application source. Les données sont uniquement affichées sur l'appareil.

### READ_DISTANCE

`android.permission.health.READ_DISTANCE`

Visible features: the distance tile with its 7-day chart, the distance detail charts, and distance records in the explorer.

- **EN:** Tidemark shows the distance the user covered today on the dashboard, with a 7-day trend, and in day, week, month and year charts in the user's units. The record explorer lists individual distance records. Data is only displayed on the device.
- **FR :** Tidemark affiche la distance parcourue dans la journée sur le tableau de bord, avec la tendance sur 7 jours, ainsi que dans des graphiques par jour, semaine, mois et année dans les unités de l'utilisateur. L'explorateur liste chaque enregistrement de distance. Les données sont uniquement affichées sur l'appareil.

### READ_ACTIVE_CALORIES_BURNED

`android.permission.health.READ_ACTIVE_CALORIES_BURNED`

Visible features: the active calories tile with its 7-day chart, the active calories detail charts, and active calories records in the explorer.

- **EN:** Tidemark shows the active calories the user burned today on the dashboard, with a 7-day trend, and in day, week, month and year charts. The record explorer lists individual active calories records. Data is only displayed on the device.
- **FR :** Tidemark affiche les calories actives brûlées dans la journée sur le tableau de bord, avec la tendance sur 7 jours, ainsi que dans des graphiques par jour, semaine, mois et année. L'explorateur liste chaque enregistrement de calories actives. Les données sont uniquement affichées sur l'appareil.

### READ_EXERCISE

`android.permission.health.READ_EXERCISE`

Visible feature: exercise sessions in the record explorer, with their type, time, title, notes, segments and laps. Tidemark does not request exercise routes.

- **EN:** Tidemark's record explorer lists the user's exercise sessions and shows each one in full: exercise type, start and end time, title, notes, segments, laps and the app that recorded it. Data is only displayed on the device. Exercise routes are not requested.
- **FR :** L'explorateur d'enregistrements de Tidemark liste les séances d'exercice de l'utilisateur et affiche chacune en détail : type d'exercice, heures de début et de fin, titre, notes, segments, tours et application qui l'a enregistrée. Les données sont uniquement affichées sur l'appareil. Les itinéraires d'exercice ne sont pas demandés.

### READ_SLEEP

`android.permission.health.READ_SLEEP`

Visible features: the last night tile with its stage bar, the sleep duration charts, the sleep stage hypnogram in the day view, and sleep sessions in the explorer.

- **EN:** Tidemark shows last night's sleep duration and stages on the dashboard, a hypnogram of each night's sleep stages, and sleep duration charts by week, month and year. The record explorer lists individual sleep sessions with their stages. Data is only displayed on the device.
- **FR :** Tidemark affiche la durée et les phases du sommeil de la nuit dernière sur le tableau de bord, un hypnogramme des phases de sommeil de chaque nuit, et des graphiques de durée de sommeil par semaine, mois et année. L'explorateur liste chaque session de sommeil avec ses phases. Les données sont uniquement affichées sur l'appareil.

### READ_HEART_RATE

`android.permission.health.READ_HEART_RATE`

Visible features: the resting heart rate day view, which plots that day's heart rate samples with the resting value as a reference line, and heart rate records with their samples in the explorer.

- **EN:** In the resting heart rate day view, Tidemark plots the user's heart rate samples for that day, with the day's resting heart rate as a reference line. The record explorer lists heart rate records and their individual samples. Data is only displayed on the device.
- **FR :** Dans la vue journalière de la fréquence cardiaque au repos, Tidemark trace les mesures de fréquence cardiaque de l'utilisateur pour ce jour, avec la fréquence cardiaque au repos du jour comme ligne de référence. L'explorateur liste les enregistrements de fréquence cardiaque et chacune de leurs mesures. Les données sont uniquement affichées sur l'appareil.

### READ_RESTING_HEART_RATE

`android.permission.health.READ_RESTING_HEART_RATE`

Visible features: the latest resting heart rate tile with its 7-day chart, the resting heart rate trend charts, and resting heart rate records in the explorer.

- **EN:** Tidemark shows the user's latest resting heart rate on the dashboard, with a 7-day trend, and its average, minimum and maximum in week, month and year charts. The record explorer lists individual resting heart rate records. Data is only displayed on the device.
- **FR :** Tidemark affiche la dernière fréquence cardiaque au repos de l'utilisateur sur le tableau de bord, avec la tendance sur 7 jours, ainsi que sa moyenne, son minimum et son maximum dans des graphiques par semaine, mois et année. L'explorateur liste chaque enregistrement de fréquence cardiaque au repos. Les données sont uniquement affichées sur l'appareil.

### READ_WEIGHT

`android.permission.health.READ_WEIGHT`

Visible features: the latest weight tile with its 30-day chart, the weight trend charts, and weight records in the explorer.

- **EN:** Tidemark shows the user's latest weight on the dashboard, with a 30-day trend, and weight charts by week, month and year in the user's units. The record explorer lists individual weight records. Data is only displayed on the device.
- **FR :** Tidemark affiche le dernier poids de l'utilisateur sur le tableau de bord, avec la tendance sur 30 jours, ainsi que des graphiques de poids par semaine, mois et année dans les unités de l'utilisateur. L'explorateur liste chaque enregistrement de poids. Les données sont uniquement affichées sur l'appareil.

### READ_HEALTH_DATA_HISTORY

`android.permission.health.READ_HEALTH_DATA_HISTORY`

Visible features: month and year charts that reach back past the first 30 days, and explorer date ranges older than that. Without it, a banner explains that only data from 30 days before the first grant is visible.

- **EN:** Lets the user view their past records. Without it, Health Connect only returns data from 30 days before Tidemark was first granted access, so the month and year charts and the record explorer could not show older history. Tidemark requests it only when the device supports it.
- **FR :** Permet à l'utilisateur de consulter ses enregistrements passés. Sans elle, Connexion Santé ne renvoie que les données des 30 jours précédant la première autorisation de Tidemark, si bien que les graphiques par mois et par année et l'explorateur ne pourraient pas afficher l'historique plus ancien. Tidemark ne la demande que si l'appareil la prend en charge.

## Permissions Tidemark does not request

- No write permission for any data type. Tidemark never writes, edits or deletes data.
- No `READ_HEALTH_DATA_IN_BACKGROUND`. Tidemark reads only while it is on screen.
- No exercise route access.
- No `INTERNET` or `ACCESS_NETWORK_STATE`. The manifest removes both, and CI fails the build if either appears in the release APK.

## Non-health permission in the APK

The APK also lists `io.reddevs.tidemark.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. AndroidX Core adds it so that receivers registered at runtime stay private to the app on Android versions before 13. It is a signature permission that only Tidemark itself holds. It gives access to no data and needs no declaration.

## Cross-check with the manifest

The nine permissions above match the `uses-permission` entries in `app/src/main/AndroidManifest.xml` one for one. If the manifest changes, update this file in the same pull request, and wait for the change to go through a GitHub release before changing the Play declaration.
