# Epic Fight Doppelganger

プレイヤータイプのエピックファイトのボスです。

Minecraft **1.20.1** / **Forge 47.4+**、**Epic Fight 20.14.17+** 向けアドオン。エンドコンテンツのボス **yourself（あなた自身）** は、スキンと防具を完全コピーし、一度でも使った武器と同じモーションで戦います。使った回復アイテムも真似ます。

## 召喚

村人を倒すと **村人の肉** が落ちます。説明文は **食べるな**。食べるとソウルの柱が立ち、**あなた自身** が現れます。再戦は何度でもできます。

## 戦闘

- 持っている武器の Epic Fight カテゴリに応じてモーションが変わる
- 一度使った武器はいつでもボスが使える（Nightfall / Weapons of Miracles も、Epic Fight が認識していれば同じ経路）
- プレイヤーのガード / パリィ / 回避スキルをコピー
- 攻撃に **毒 II**
- 3フェーズ。体力低下で **攻撃力上昇 I / II / III**（バニラのエフェクト）
- 基礎体力 500（プレイヤーの最大体力でスケール可能）
- あまり怯まない
- マルチは最初にヘイトを取ったプレイヤーが主ターゲット。モブに殴られるとそのモブにも敵対

## 報酬

- 鏡映の霊薬 ×5（耐性IV + 再生IV、100分）
- ネザライトブロック ×5
- ダイヤモンドブロック ×5
- 経験値 12000（エンダードラゴン相当）

## ビルド

JDK 17。このフォルダを IntelliJ で開き、`./gradlew genIntellijRuns` のあと `./gradlew build`。jar は `build/libs/`。

`gradlew` が無い場合は Gradle 8.8 を入れ、`gradle wrapper --gradle-version 8.8` を実行してからビルド。

Epic Fight は CurseMaven（`epic-fight-mod-405076:8049910` = 20.14.17）から引きます。

## 設定

`config/efdoppelganger-common.toml`

- 基礎体力、プレイヤー体力によるスケール
- 毒 II の持続時間
- スタン装甲
- 経験値とドロップ数
- 武器持ち替え間隔
- 回復を使う HP%

## Language

`en_us` / `ja_jp`.

---

An Epic Fight **player-type** endgame boss. It copies your skin and armor, remembers every weapon you have used (including the Epic Fight animation), and fights with those motions. Healing items you used are remembered too.

Hits apply **Poison II**. Phases 1–3 apply **Strength I / II / III**. Default 500 HP. Spawn by eating villager meat.

## License

GPL-3.0-or-later (required by Epic Fight).
