/* ===================================================================
   SnsTimeLineApp モックアップ 共通スクリプト

   これは表示イメージ確認用のモックであり、実装ではない。
   通信は一切行わず、すべてブラウザ内で完結する。
   データ構造は docs/05_api_design.md のレスポンススキーマに沿わせている。
   =================================================================== */

/* ===== ダミーデータ ===== */
/* メールアドレスは example.com のみを使う（CLAUDE.md 6章） */

const CURRENT_USER_ID = 1;

const USERS = [
  {
    id: 1,
    username: 'taro_123',
    displayName: 'たろう',
    avatarUrl: null,
    bio: '学習目的でSNSアプリを作っています。',
    postCount: 56,
    followingCount: 12,
    followerCount: 34,
    isFollowing: false,
    isMe: true,
    createdAt: '2026-08-01T00:00:00Z',
    color: '#1d9bf0',
  },
  {
    id: 2,
    username: 'hanako',
    displayName: 'はなこ',
    avatarUrl: null,
    bio: 'カフェ巡りと写真が好きです。週末はだいたい出かけています。',
    postCount: 128,
    followingCount: 40,
    followerCount: 96,
    isFollowing: true,
    isMe: false,
    createdAt: '2026-06-12T00:00:00Z',
    color: '#f91880',
  },
  {
    id: 3,
    username: 'ken_dev',
    displayName: 'けん',
    avatarUrl: null,
    bio: 'バックエンドエンジニア。PostgreSQL とインデックス設計の話が好き。',
    postCount: 74,
    followingCount: 18,
    followerCount: 52,
    isFollowing: true,
    isMe: false,
    createdAt: '2026-05-03T00:00:00Z',
    color: '#00ba7c',
  },
  {
    id: 4,
    username: 'mika_photo',
    displayName: 'みか',
    avatarUrl: null,
    bio: '風景写真をゆるく撮っています。',
    postCount: 210,
    followingCount: 88,
    followerCount: 174,
    isFollowing: false,
    isMe: false,
    createdAt: '2026-04-21T00:00:00Z',
    color: '#6c5ce7',
  },
  {
    id: 5,
    username: 'sho_run',
    displayName: 'しょう',
    avatarUrl: null,
    bio: '毎朝走っています。フルマラソン完走が目標。',
    postCount: 45,
    followingCount: 22,
    followerCount: 31,
    isFollowing: false,
    isMe: false,
    createdAt: '2026-07-08T00:00:00Z',
    color: '#e17055',
  },
  {
    id: 6,
    username: 'yui_book',
    displayName: 'ゆい',
    avatarUrl: null,
    bio: '読んだ本の記録用。月に5冊くらい。',
    postCount: 92,
    followingCount: 33,
    followerCount: 67,
    isFollowing: true,
    isMe: false,
    createdAt: '2026-03-15T00:00:00Z',
    color: '#0984e3',
  },
];

const POST_TEXTS = [
  { body: '今日はいい天気でした。散歩がてら近所の公園まで歩いたら、思ったより人が多くてびっくりしました。', img: 'img-a' },
  { body: 'カーソルページネーションの実装、同じ時刻の投稿が2件あると取りこぼすことに気づいた。主キーをタイブレーカーに入れて解決。', img: null },
  { body: '朝のコーヒーがいちばん落ち着く時間かもしれない。', img: 'img-b' },
  { body: 'いいね数を非正規化カウンタで持つことにした。速いけれど、そのぶん整合性の責任が発生する。トランザクション設計が大事。', img: null },
  { body: '週末に撮った写真。空の色がきれいに出た。', img: 'img-c' },
  { body: '読んでいた本を読み終えた。後半の展開がよかったので、しばらく余韻に浸っています。', img: null },
  { body: '走った距離が今月で100kmを超えました。続けているとちゃんと積み上がるものですね。', img: null },
  { body: 'インデックスが効いていないクエリを EXPLAIN ANALYZE で見つけた。Seq Scan になっていた。', img: null },
  { body: '近所に新しいパン屋さんができていた。明日の朝に行ってみようと思います。', img: 'img-b' },
  { body: '夕方の空がきれいだったので、思わず立ち止まって撮ってしまった。', img: 'img-a' },
  { body: '論理削除を入れるとき、全部のクエリに条件を足すのを忘れると事故になる。リポジトリ層で徹底するのが大事。', img: null },
  { body: '今日は一日中作業していたので、少し外の空気を吸ってきます。', img: null },
  { body: '本棚を整理したら、読みかけの本が思ったより多かった。まずは1冊ずつ片付けます。', img: 'img-d' },
  { body: 'JWT の有効期限を24時間にした。失効管理をしない前提なので、そのぶん期限は短めにしておきたい。', img: null },
  { body: '朝の空気がだんだん涼しくなってきました。走るにはいい季節です。', img: null },
  { body: '画像のアップロードは投稿とは別のAPIに分けた。投稿APIをJSONのまま保てるのが気持ちいい。', img: null },
  { body: 'お昼に食べたスープがおいしかったので、作り方を調べてみようと思います。', img: 'img-c' },
  { body: '久しぶりに映画を観た。長編だったけれど、最後まで飽きずに観られました。', img: null },
  { body: 'N+1問題、頭では分かっていても実際にログを見ると想像以上にクエリが出ていて驚いた。', img: null },
  { body: '散歩コースを変えてみたら、知らない路地を見つけた。近所でもまだ知らない場所がある。', img: 'img-a' },
  { body: '今日のところは順調。明日もこの調子で進めたいです。', img: null },
  { body: '部分インデックスという仕組みを知った。削除済みの行を含めないぶん軽くなるらしい。', img: null },
  { body: 'コーヒー豆を新しいものに変えてみた。前のより少し酸味が強い。', img: 'img-b' },
  { body: '要件定義の書類を一通り書き終えた。ここまで来ると実装が楽しみになってきます。', img: null },
  { body: '夜になって少し冷えてきました。あたたかくしてお過ごしください。', img: null },
];

/** 投稿ダミーを生成する（新しい順） */
function buildPosts() {
  const now = Date.now();
  // 6人に均等に散らす（連続で同じ人が並ばないよう素数の5を掛ける）
  return POST_TEXTS.map((t, i) => {
    const author = USERS[(i * 5) % USERS.length];
    return {
      id: 100 + i,
      author: {
        id: author.id,
        username: author.username,
        displayName: author.displayName,
        avatarUrl: author.avatarUrl,
        color: author.color,
      },
      body: t.body,
      images: t.img ? [{ fileId: 20 + i, cls: t.img, width: 1200, height: 800 }] : [],
      likeCount: [12, 3, 41, 8, 25, 0, 17, 6, 33, 9, 2, 14, 5, 21, 1, 11, 38, 7, 19, 4, 0, 27, 13, 46, 10][i],
      commentCount: [3, 1, 8, 0, 5, 0, 2, 1, 6, 0, 0, 4, 1, 3, 0, 2, 7, 1, 3, 0, 0, 5, 2, 9, 1][i],
      isLikedByMe: i % 4 === 0,
      createdAt: new Date(now - (i * 2.4 + 0.3) * 3600 * 1000).toISOString(),
      editedAt: i === 3 ? new Date(now - 6 * 3600 * 1000).toISOString() : null,
    };
  });
}

const POSTS = buildPosts();

const COMMENTS = [
  { id: 500, authorId: 3, body: 'いいですね！わたしもその公園よく行きます。', createdAt: hoursAgo(5), editedAt: null, isMine: false },
  { id: 501, authorId: 2, body: '天気がいいと歩くだけで気持ちいいですよね。', createdAt: hoursAgo(4), editedAt: null, isMine: false },
  { id: 502, authorId: 1, body: 'ありがとうございます。またゆっくり行ってみます。', createdAt: hoursAgo(2), editedAt: hoursAgo(1), isMine: true },
];

function hoursAgo(h) {
  return new Date(Date.now() - h * 3600 * 1000).toISOString();
}

function getUser(id) {
  return USERS.find((u) => u.id === id);
}

/* ===== 表示ヘルパー ===== */

/** 24時間以内は相対表示（3時間前）、それ以降は絶対表示（8月15日）。docs 5.1 */
function formatRelative(iso) {
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diffMs / 60000);
  if (min < 1) return 'たった今';
  if (min < 60) return `${min}分前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}時間前`;
  const d = new Date(iso);
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}

/** 投稿詳細用の絶対表示（2026年8月15日 14:32）。docs SC-04 */
function formatAbsolute(iso) {
  const d = new Date(iso);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${hh}:${mm}`;
}

/** 「2026年8月からご利用」。docs SC-05 */
function formatJoined(iso) {
  const d = new Date(iso);
  return `${d.getFullYear()}年${d.getMonth() + 1}月からご利用`;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
}

/** アバター（未設定時はイニシャルを表示）。docs 5章 */
function avatarHtml(user, sizeClass) {
  const initial = escapeHtml(user.displayName.charAt(0));
  const cls = sizeClass ? `avatar ${sizeClass}` : 'avatar';
  return `<span class="${cls}" style="background:${user.color || '#1d9bf0'}">${initial}</span>`;
}

/* ===== 投稿カード（docs 5.1） =====
   アクション行に並ぶのは「コメント」と「いいね」の2つだけ。
   インプレッション数もリツイートも置かない（差別化ポイント）。 */
function postCardHtml(post, opts) {
  const o = opts || {};
  const isMine = post.author.id === CURRENT_USER_ID;
  const edited = post.editedAt ? ' <span class="edited">・編集済み</span>' : '';

  const moreMenu = isMine
    ? `<div class="more-menu">
         <button class="more-btn" type="button" data-more aria-label="メニュー">⋯</button>
         <div class="dropdown" data-more-menu>
           <button type="button" data-open-modal="modal-edit-post">✏️ 編集</button>
           <button type="button" data-open-modal="modal-delete" data-delete-target="post">🗑 削除</button>
         </div>
       </div>`
    : '';

  const image = post.images.length
    ? `<div class="post-image ${post.images[0].cls}">添付画像（モック）</div>`
    : '';

  return `
  <article class="post-card" data-post-id="${post.id}" data-href="post-detail.html">
    <a href="profile.html" data-stop>${avatarHtml(post.author)}</a>
    <div class="post-body-col">
      <div class="post-head">
        <a class="name" href="profile.html" data-stop>${escapeHtml(post.author.displayName)}</a>
        <span class="handle">@${escapeHtml(post.author.username)}</span>
        <span class="handle">·</span>
        <span class="time">${formatRelative(post.createdAt)}</span>${edited}
        <span class="spacer"></span>
        ${moreMenu}
      </div>
      <p class="post-text${o.clamp === false ? '' : ' is-clamped'}">${escapeHtml(post.body)}</p>
      ${image}
      <div class="post-actions">
        <a class="action-btn action-comment" href="post-detail.html" data-stop>
          <span class="ico">💬</span><span>${post.commentCount}</span>
        </a>
        <button class="action-btn action-like${post.isLikedByMe ? ' is-liked' : ''}"
                type="button" data-like data-stop aria-label="いいね">
          <span class="ico">${post.isLikedByMe ? '♥' : '♡'}</span>
          <span data-like-count>${post.likeCount}</span>
        </button>
      </div>
    </div>
  </article>`;
}

/** ユーザー行（SC-07 / 08 / 09 / 10 共通） */
function userRowHtml(user) {
  const followBtn = user.isMe
    ? ''
    : (user.isFollowing
      ? `<button class="btn btn-outline btn-sm btn-following" type="button" data-follow data-following="true">
           <span class="label-following">フォロー中</span><span class="label-unfollow">フォロー解除</span>
         </button>`
      : `<button class="btn btn-primary btn-sm" type="button" data-follow data-following="false">フォロー</button>`);

  return `
  <div class="user-row">
    <a href="profile.html">${avatarHtml(user)}</a>
    <div class="user-main">
      <a class="user-name" href="profile.html">${escapeHtml(user.displayName)}</a>
      <span class="user-handle">@${escapeHtml(user.username)}</span>
      <div class="user-bio">${escapeHtml(user.bio || '')}</div>
    </div>
    ${followBtn}
  </div>`;
}

/** コメント1件（SC-04）。F-CM-03 のインライン編集フォームも合わせて仕込む（D-51） */
function commentHtml(c) {
  const author = getUser(c.authorId);
  const edited = c.editedAt ? ' <span class="edited">・編集済み</span>' : '';
  const menu = c.isMine
    ? `<div class="more-menu">
         <button class="more-btn" type="button" data-more aria-label="メニュー">⋯</button>
         <div class="dropdown" data-more-menu>
           <button type="button" data-edit-comment>✏️ 編集</button>
           <button type="button" data-open-modal="modal-delete" data-delete-target="comment">🗑 削除</button>
         </div>
       </div>`
    : '';

  return `
  <div class="comment-item" data-comment-id="${c.id}">
    <a href="profile.html">${avatarHtml(author, 'avatar-sm')}</a>
    <div class="comment-main">
      <div class="post-head">
        <a class="name" href="profile.html">${escapeHtml(author.displayName)}</a>
        <span class="handle">@${escapeHtml(author.username)}</span>
        <span class="handle">·</span>
        <span class="time" data-comment-time>${formatRelative(c.createdAt)}</span><span data-comment-edited>${edited}</span>
        <span class="spacer"></span>
        ${menu}
      </div>
      <p class="comment-text" data-comment-text>${escapeHtml(c.body)}</p>
      <div class="comment-edit" hidden>
        <textarea class="textarea" rows="2" data-edit-textarea>${escapeHtml(c.body)}</textarea>
        <div class="comment-form-foot">
          <span class="char-counter" data-edit-counter>0/280</span>
          <button class="btn btn-outline btn-sm" type="button" data-cancel-edit>キャンセル</button>
          <button class="btn btn-accent btn-sm" type="button" data-save-edit>保存</button>
        </div>
      </div>
    </div>
  </div>`;
}

/* ===== トースト（画面下部・3秒で自動消滅。docs 5章） ===== */
function showToast(message, isError) {
  let area = document.querySelector('.toast-area');
  if (!area) {
    area = document.createElement('div');
    area.className = 'toast-area';
    document.body.appendChild(area);
  }
  const el = document.createElement('div');
  el.className = 'toast' + (isError ? ' is-error' : '');
  el.textContent = message;
  area.appendChild(el);

  setTimeout(() => {
    el.classList.add('is-leaving');
    setTimeout(() => el.remove(), 260);
  }, 3000);
}

/* ===== モーダル（背景クリックとESCで閉じる。docs 5章） ===== */
function openModal(id) {
  const m = document.getElementById(id);
  if (m) m.classList.add('is-open');
}

function closeModal(el) {
  el.classList.remove('is-open');
}

function closeAllModals() {
  document.querySelectorAll('.modal-backdrop.is-open').forEach(closeModal);
}

/* ===== 文字数カウンタ =====
   残り20文字で警告色、超過で赤＋ボタン無効化。docs MD-01 */
function bindCharCounter(textarea, counter, submitBtn, max) {
  if (!textarea || !counter) return;
  const limit = max || 280;

  const update = () => {
    const len = [...textarea.value].length; // サロゲートペアを1文字として数える
    counter.textContent = `${len}/${limit}`;
    counter.classList.toggle('is-warn', len > limit - 20 && len <= limit);
    counter.classList.toggle('is-over', len > limit);

    if (submitBtn) {
      const invalid = len === 0 || len > limit || textarea.value.trim().length === 0;
      submitBtn.disabled = invalid;
      submitBtn.classList.toggle('is-disabled', invalid);
    }
  };

  textarea.addEventListener('input', update);
  update();
}

/* ===== 共通のイベント束ね ===== */
document.addEventListener('DOMContentLoaded', () => {

  /* --- いいね（楽観的UI更新・冪等） ---
     押下と同時にUIを更新する。連打しても2にならない（F-LK-01の冪等性）。 */
  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-like]');
    if (!btn) return;
    e.preventDefault();
    e.stopPropagation();

    const liked = btn.classList.toggle('is-liked');
    const countEl = btn.querySelector('[data-like-count]');
    const ico = btn.querySelector('.ico');
    let n = parseInt(countEl.textContent, 10) || 0;

    // トグルなので、既にいいね済みの状態でもう一度押しても +2 にはならない
    countEl.textContent = liked ? n + 1 : Math.max(0, n - 1);
    ico.textContent = liked ? '♥' : '♡';

    // 詳細画面の「12 いいね」行があれば追従させる
    const detailCount = document.querySelector('[data-detail-like-count]');
    if (detailCount) detailCount.textContent = countEl.textContent;
  });

  /* --- フォロー（楽観的UI更新） --- */
  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-follow]');
    if (!btn) return;
    e.preventDefault();
    e.stopPropagation();

    const following = btn.dataset.following === 'true';

    if (following) {
      btn.dataset.following = 'false';
      btn.className = btn.className.replace('btn-outline', 'btn-primary').replace(' btn-following', '');
      btn.textContent = 'フォロー';
      showToast('フォローを解除しました');
    } else {
      btn.dataset.following = 'true';
      btn.className = btn.className.replace('btn-primary', 'btn-outline') + ' btn-following';
      btn.innerHTML = '<span class="label-following">フォロー中</span><span class="label-unfollow">フォロー解除</span>';
      showToast('フォローしました');
    }

    // プロフィール画面のフォロワー数を追従させる
    const fc = document.querySelector('[data-follower-count]');
    if (fc) {
      const cur = parseInt(fc.textContent.replace(/,/g, ''), 10) || 0;
      fc.textContent = following ? Math.max(0, cur - 1) : cur + 1;
    }
  });

  /* --- [⋯] メニューの開閉 --- */
  document.addEventListener('click', (e) => {
    const trigger = e.target.closest('[data-more], [data-menu-trigger]');
    const openMenus = document.querySelectorAll('[data-more-menu].is-open, [data-menu].is-open');

    if (!trigger) {
      openMenus.forEach((m) => m.classList.remove('is-open'));
      return;
    }

    e.preventDefault();
    e.stopPropagation();

    const menu = trigger.parentElement.querySelector('[data-more-menu], [data-menu]');
    openMenus.forEach((m) => { if (m !== menu) m.classList.remove('is-open'); });
    if (menu) menu.classList.toggle('is-open');
  });

  /* --- モーダルを開く / 閉じる --- */
  document.addEventListener('click', (e) => {
    const opener = e.target.closest('[data-open-modal]');
    if (opener) {
      e.preventDefault();
      e.stopPropagation();

      // 削除確認は対象に応じて文言を切り替える（docs MD-03）
      const target = opener.dataset.deleteTarget;
      if (target) {
        const title = document.querySelector('[data-delete-title]');
        if (title) {
          title.textContent = target === 'comment' ? 'コメントを削除しますか？' : '投稿を削除しますか？';
        }
      }

      document.querySelectorAll('[data-more-menu].is-open').forEach((m) => m.classList.remove('is-open'));
      openModal(opener.dataset.openModal);
      return;
    }

    const closer = e.target.closest('[data-close-modal]');
    if (closer) {
      e.preventDefault();
      closeModal(closer.closest('.modal-backdrop'));
      return;
    }

    // 背景クリックで閉じる
    if (e.target.classList.contains('modal-backdrop')) closeModal(e.target);
  });

  // ESCで閉じる
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeAllModals();
  });

  /* --- 投稿カード全体のクリックで詳細へ ---
     アバター・表示名・いいね・[⋯] は data-stop で伝播を止める（docs 5.1） */
  document.addEventListener('click', (e) => {
    if (e.target.closest('[data-stop], [data-more], [data-more-menu], .more-menu')) return;
    const card = e.target.closest('[data-href]');
    if (card) location.href = card.dataset.href;
  });

  /* --- F-CM-03 コメント編集（インライン。docs/09_decision_log.md D-51） ---
     本文 <p> を隠し .comment-edit を表示する。モーダルは使わない。 */
  document.addEventListener('click', (e) => {
    const editBtn = e.target.closest('[data-edit-comment]');
    if (editBtn) {
      e.preventDefault();
      e.stopPropagation();
      document.querySelectorAll('[data-more-menu].is-open').forEach((m) => m.classList.remove('is-open'));

      const item = editBtn.closest('.comment-item');
      const text = item.querySelector('[data-comment-text]');
      const editForm = item.querySelector('.comment-edit');
      const textarea = editForm.querySelector('[data-edit-textarea]');
      const counter = editForm.querySelector('[data-edit-counter]');
      const saveBtn = editForm.querySelector('[data-save-edit]');

      text.hidden = true;
      editForm.hidden = false;
      textarea.value = text.textContent;
      bindCharCounter(textarea, counter, saveBtn, 280);
      textarea.focus();
      return;
    }

    const cancelBtn = e.target.closest('[data-cancel-edit]');
    if (cancelBtn) {
      e.preventDefault();
      const item = cancelBtn.closest('.comment-item');
      item.querySelector('[data-comment-text]').hidden = false;
      item.querySelector('.comment-edit').hidden = true;
      return;
    }

    const saveBtn = e.target.closest('[data-save-edit]');
    if (saveBtn) {
      e.preventDefault();
      const item = saveBtn.closest('.comment-item');
      const textarea = item.querySelector('[data-edit-textarea]');
      const body = textarea.value.trim();
      if (!body || [...textarea.value].length > 280) return;

      const text = item.querySelector('[data-comment-text]');
      text.textContent = body;
      text.hidden = false;
      item.querySelector('.comment-edit').hidden = true;

      const editedEl = item.querySelector('[data-comment-edited]');
      if (editedEl) editedEl.innerHTML = ' <span class="edited">・編集済み</span>';

      showToast('コメントを編集しました');
    }
  });
});
