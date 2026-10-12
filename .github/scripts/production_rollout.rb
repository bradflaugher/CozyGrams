# Finds the staged rollout on the production track and prints its version code.
#
#     bundle exec ruby .github/scripts/production_rollout.rb <package> <json key> [version code]
#
# play-promote.yml runs this before "update rollout". fastlane supply's
# update_rollout picks a draft release as readily as an in-progress one, and
# completing a draft would publish it to everyone, so only a release that is
# really rolling out (and the given version code, if any) is accepted. The
# gems come with fastlane (Gemfile.lock).
require "googleauth"
require "google/apis/androidpublisher_v3"

package, key, version_code = ARGV
abort "usage: production_rollout.rb <package> <json key> [version code]" if package.to_s.empty? || key.to_s.empty?

publisher = Google::Apis::AndroidpublisherV3
service = publisher::AndroidPublisherService.new
service.authorization = Google::Auth::ServiceAccountCredentials.make_creds(
  json_key_io: File.open(key),
  scope: publisher::AUTH_ANDROIDPUBLISHER
)

# Tracks are read through a throwaway edit, deleted again without committing.
edit = service.insert_edit(package)
begin
  track = service.get_edit_track(package, edit.id, "production")
ensure
  service.delete_edit(package, edit.id)
end

releases = (track.releases || []).select { |release| release.status == "inProgress" }
unless version_code.to_s.empty?
  releases = releases.select { |release| (release.version_codes || []).map(&:to_s).include?(version_code) }
end

if releases.size != 1
  wanted = version_code.to_s.empty? ? "a staged rollout" : "a staged rollout of version code #{version_code}"
  others = (track.releases || []).map { |release| "#{release.status} #{(release.version_codes || []).join(',')}" }
  abort "Production has no #{wanted} to update (production releases: #{others.empty? ? 'none' : others.join('; ')})"
end

puts releases.first.version_codes.max
