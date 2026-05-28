export const maskEmail = (email: string) => {
    if (!email) return '';
    const [id, domain] = email.split('@');
    // 아이디가 3글자 이하면 앞 1글자만, 아니면 앞 3글자만 보여주기
    const visibleId = id.length <= 3 ? id[0] : id.substring(0, 3);
    return `${visibleId}****@${domain}`;
};

export const maskBusinessNumber = (num: string) => {
    if (!num) return '';
    // 123-45-67890 형식에서 뒷부분 일부 마스킹 예시
    return num.replace(/(\d{3})-(\d{2})-(\d{5})/, '$1-$2-*****');
};
